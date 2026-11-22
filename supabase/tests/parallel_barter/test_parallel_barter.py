"""Integration regression tests; starts a disposable PostgreSQL via PG_BIN.
Requires PostgreSQL 14+ binaries and no Python packages. Never contacts Supabase.
"""
from pathlib import Path
import concurrent.futures,json,os,subprocess,tempfile,time

HERE=Path(__file__).resolve().parent
BIN=Path(os.environ.get('PG_BIN','/usr/bin'))
fixture=(HERE/'baseline-fixture.sql').read_text()
migration=(HERE.parents[1]/'migrations/20261121000100_parallel_barter.sql').read_text()
work=Path(tempfile.mkdtemp(prefix='cloble-pg-'));socket=work/'socket';socket.mkdir()
env=dict(os.environ,PGHOST=str(socket),PGPORT='55439',PGUSER='postgres',PGDATABASE='postgres')

def sql(query,check=True):
    r=subprocess.run([str(BIN/'psql'),'-X','-qAt','-v','ON_ERROR_STOP=1','-c',query],env=env,text=True,capture_output=True,timeout=20)
    if check and r.returncode:raise AssertionError(r.stderr+'\nSQL: '+query[:350])
    return r

def scalar(query):return sql(query).stdout.strip()
def rpc(user,fn,args):
    args=','.join(str(x) if isinstance(x,int) else "'"+x.replace("'","''")+"'" for x in args)
    return json.loads(scalar(f"SET test.user_id='{user}'; SELECT public.{fn}({args});"))
def code(result,expected='SUCCESS'):
    assert result.get('code')==expected,(expected,result)
    return result

def seed():
    sql('TRUNCATE notifications,matches,interests,swipe_passes,clothing_items,users RESTART IDENTITY CASCADE;')
    sql("INSERT INTO users VALUES (1,'A'),(2,'B'),(3,'C'); INSERT INTO clothing_items(id,\"ownerId\",name) SELECT u.id*100+i,u.id,'Item '||u.name||i FROM users u CROSS JOIN generate_series(1,10) i;")

def send(user,item):return code(rpc(user,'rpc_send_interest',[user,item,100]))
def respond(user,interest,item):return code(rpc(user,'rpc_respond_to_interest_and_create_match',[user,interest,item,101]))

def fresh_baseline():
    sql('DROP SCHEMA public CASCADE; CREATE SCHEMA public;')
    sql(fixture)
    seed()

def migrated():
    fresh_baseline();sql(migration)

subprocess.run([str(BIN/'initdb'),'-D',str(work/'data'),'-A','trust','-U','postgres','--no-locale'],capture_output=True,text=True,check=True)
started=False
try:
    subprocess.run([str(BIN/'pg_ctl'),'-D',str(work/'data'),'-l',str(work/'server.log'),'-o',f"-k {socket} -p 55439 -c listen_addresses='' -c fsync=off -c synchronous_commit=off",'-w','start'],capture_output=True,text=True,check=True)
    started=True
    print(scalar('SELECT version();'),flush=True)
    fresh_baseline()
    sql('CREATE ROLE test_app; REVOKE EXECUTE ON FUNCTION public.rpc_send_interest(bigint,bigint,bigint,boolean) FROM PUBLIC; GRANT EXECUTE ON FUNCTION public.rpc_send_interest(bigint,bigint,bigint,boolean) TO test_app;')
    first=send(1,201);match=respond(2,first['interestId'],101)['matchId']
    code(rpc(1,'rpc_send_interest',[1,202,100]),'PAIR_ALREADY_MATCHED')
    print('PASS: baseline reproduces the same-partner blocker',flush=True)
    acl=scalar("SELECT coalesce(proacl::text,'') FROM pg_proc WHERE oid='public.rpc_send_interest(bigint,bigint,bigint,boolean)'::regprocedure")
    sql(migration)
    assert scalar(f'SELECT status FROM matches WHERE id={match}')=='ACTIVE'
    assert acl==scalar("SELECT coalesce(proacl::text,'') FROM pg_proc WHERE oid='public.rpc_send_interest(bigint,bigint,bigint,boolean)'::regprocedure")
    for i in range(2,5):
        sent=send(1,200+i);respond(2,sent['interestId'],100+i)
    assert scalar("SELECT count(*) FROM matches WHERE status='ACTIVE'")=='4'
    assert scalar("SELECT count(*) FROM notifications WHERE type='MATCH_VERIFIED'")=='8'
    sql(migration)
    print('PASS: four parallel matches; existing match, notifications and function ACL preserved; migration is rerunnable',flush=True)

    code(rpc(3,'rpc_send_interest',[1,205,100]),'SENDER_NOT_FOUND')
    fifth=send(1,205)
    code(rpc(1,'rpc_respond_to_interest_and_create_match',[2,fifth['interestId'],105,101]),'INVALID_ITEM_OWNERSHIP')
    code(rpc(3,'rpc_respond_to_interest_and_create_match',[3,fifth['interestId'],105,101]),'INTEREST_ALREADY_PROCESSED_OR_INVALID')
    code(rpc(2,'rpc_respond_to_interest_and_create_match',[2,fifth['interestId'],301,101]),'INVALID_ITEM_OWNERSHIP')
    print('PASS: session, receiver and ownership checks preserved',flush=True)

    for item1,item2 in [(101,206),(206,101),(201,206),(206,201)]:
        interest=int(scalar('INSERT INTO interests("senderUserId","receiverUserId","targetItemId",status) VALUES (1,2,206,\'INVALIDATED\') RETURNING id'))
        result=sql(f'INSERT INTO matches("sourceInterestId","user1Id","user1ItemId","user2Id","user2ItemId",status) VALUES ({interest},1,{item1},2,{item2},\'ACTIVE\')',check=False)
        assert result.returncode and 'matches_active_items_excl' in result.stderr,result
    print('PASS: database prevents item reuse in either column and either position',flush=True)

    matches=[int(x) for x in scalar('SELECT id FROM matches ORDER BY id').splitlines()]
    code(rpc(1,'rpc_cancel_match',[matches[0],1,102]))
    code(rpc(2,'rpc_complete_match_exchange',[matches[1],2,'Kampus',103]))
    assert scalar(f'SELECT status FROM matches WHERE id={matches[0]}')=='CANCELLED'
    assert scalar(f'SELECT status FROM matches WHERE id={matches[1]}')=='COMPLETED'
    assert scalar("SELECT count(*) FROM matches WHERE status='ACTIVE'")=='2'
    assert scalar('SELECT status FROM clothing_items WHERE id=101')=='AVAILABLE'
    assert scalar('SELECT status FROM clothing_items WHERE id=102')=='EXCHANGED'
    assert scalar('SELECT status FROM clothing_items WHERE id=103')=='AVAILABLE'
    print('PASS: cancellation/completion affect only their match and its items',flush=True)

    seed()
    baseline=send(1,201);respond(2,baseline['interestId'],101)
    available=send(1,202);send(1,203)
    locking=send(3,203);respond(2,locking['interestId'],301)
    mutual=send(2,102)
    assert mutual.get('isMutualMatch') is True,mutual
    actual=scalar(f'SELECT "sourceInterestId"||\':\'||"user1ItemId"||\':\'||"user2ItemId" FROM matches WHERE id={mutual["matchId"]}')
    assert actual==f'{available["interestId"]}:102:202',actual
    print('PASS: cross-interest match skips locked reverse items, even with an existing same-partner match',flush=True)

    seed()
    one=send(1,201);two=send(1,202)
    def competing(interest):
        out=scalar(f"BEGIN; SET test.user_id='2'; SELECT public.rpc_respond_to_interest_and_create_match(2,{interest},101,104); SELECT pg_sleep(0.3); COMMIT;")
        return json.loads(next(line for line in out.splitlines() if line.startswith('{')))
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        results=list(pool.map(competing,[one['interestId'],two['interestId']]))
    assert sorted(r['code'] for r in results)==['ITEM_ALREADY_IN_ACTIVE_MATCH','SUCCESS'],results
    assert scalar("SELECT count(*) FROM matches WHERE status='ACTIVE'")=='1'
    assert scalar("SELECT count(*) FROM notifications WHERE type='MATCH_VERIFIED'")=='2'
    print('PASS: two simultaneous requests sharing an item create exactly one match and one notification pair',flush=True)

    # Bypass RPC checks to test the exclusion constraint with simultaneous writers.
    seed()
    source1=int(scalar('INSERT INTO interests("senderUserId","receiverUserId","targetItemId",status) VALUES (1,2,201,\'PENDING\') RETURNING id'))
    source2=int(scalar('INSERT INTO interests("senderUserId","receiverUserId","targetItemId",status) VALUES (2,1,101,\'PENDING\') RETURNING id'))
    def direct(values):
        iid,u1,it1,u2,it2=values
        return sql(f"BEGIN; INSERT INTO matches(\"sourceInterestId\",\"user1Id\",\"user1ItemId\",\"user2Id\",\"user2ItemId\",status) VALUES ({iid},{u1},{it1},{u2},{it2},'ACTIVE'); SELECT pg_sleep(0.3); COMMIT;",check=False)
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        results=list(pool.map(direct,[(source1,1,101,2,201),(source2,2,202,1,101)]))
    assert sum(r.returncode==0 for r in results)==1
    assert any('matches_active_items_excl' in r.stderr or 'deadlock detected' in r.stderr for r in results), [(r.returncode, r.stderr) for r in results]
    print('PASS: concurrent direct inserts cannot bypass cross-column item exclusivity',flush=True)

    # Force a reservation failure after RPC has written an interest. Its handler
    # must roll back the complete function block, including that earlier write.
    seed();reverse=send(1,201)
    sql('CREATE FUNCTION inject_item_conflict() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION USING ERRCODE=\'23P01\',CONSTRAINT=\'matches_active_items_excl\',MESSAGE=\'test conflict\'; END $$; CREATE TRIGGER inject_item_conflict BEFORE INSERT ON matches FOR EACH ROW EXECUTE FUNCTION inject_item_conflict();')
    before=scalar('SELECT count(*) FROM interests')
    code(rpc(2,'rpc_send_interest',[2,101,105]),'TARGET_ITEM_NOT_ELIGIBLE')
    assert scalar('SELECT count(*) FROM interests')==before
    assert scalar(f'SELECT status FROM interests WHERE id={reverse["interestId"]}')=='PENDING'
    assert scalar("SELECT count(*) FROM notifications WHERE type='MATCH_VERIFIED'")=='0'
    code(rpc(2,'rpc_respond_to_interest_and_create_match',[2,reverse['interestId'],101,105]),'ITEM_ALREADY_IN_ACTIVE_MATCH')
    assert scalar(f'SELECT status FROM interests WHERE id={reverse["interestId"]}')=='PENDING'
    sql('DROP TRIGGER inject_item_conflict ON matches; DROP FUNCTION inject_item_conflict();')
    print('PASS: reservation conflicts return domain errors and roll back partial interest/notification writes',flush=True)

    fresh_baseline()
    one=send(1,201);respond(2,one['interestId'],101)
    other=int(scalar('INSERT INTO interests("senderUserId","receiverUserId","targetItemId",status) VALUES (3,2,202,\'ACCEPTED_MATCHED\') RETURNING id'))
    sql(f'INSERT INTO matches("sourceInterestId","user1Id","user1ItemId","user2Id","user2ItemId",status) VALUES ({other},3,301,2,201,\'ACTIVE\')')
    failed=sql(migration,check=False)
    assert failed.returncode and 'already used by multiple active matches' in failed.stderr
    assert scalar("SELECT to_regclass('public.index_matches_unique_active_user_pair') IS NOT NULL")=='t'
    assert scalar('SELECT count(*) FROM matches')=='2'
    print('PASS: preflight refuses existing duplicate item reservations without deleting data',flush=True)

    fresh_baseline()
    sql("CREATE OR REPLACE FUNCTION public.rpc_send_interest(p_sender_user_id bigint,p_target_item_id bigint,p_now_millis bigint,p_delete_swipe_pass boolean DEFAULT false) RETURNS jsonb LANGUAGE sql AS $$ SELECT '{}'::jsonb $$")
    failed=sql(migration,check=False)
    assert failed.returncode and 'differs from the inspected version' in failed.stderr
    assert scalar("SELECT to_regclass('public.index_matches_unique_active_user_pair') IS NOT NULL")=='t'
    assert scalar("SELECT count(*) FROM pg_constraint WHERE conname='matches_active_items_excl'")=='0'
    print('PASS: unexpected server-function changes abort the entire migration',flush=True)
    print('ALL POSTGRESQL REGRESSION CHECKS PASSED',flush=True)
finally:
    if started:subprocess.run([str(BIN/'pg_ctl'),'-D',str(work/'data'),'-m','immediate','-w','stop'],capture_output=True,text=True,timeout=15)
    print('Stopped disposable PostgreSQL:',work,flush=True)
