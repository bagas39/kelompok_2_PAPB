"""Run with PG_BIN=/path/to/postgresql/bin python3 test_explicit_match_confirmation.py.
Uses a disposable PostgreSQL database and never connects to Supabase.
"""
from pathlib import Path
import concurrent.futures
import json
import os
import subprocess
import tempfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
BIN = Path(os.environ.get('PG_BIN', '/usr/bin'))
fixture = (HERE.parent / 'parallel_barter/baseline-fixture.sql').read_text()
previous = (ROOT / 'supabase/migrations/20261121000100_parallel_barter.sql').read_text()
migration = (ROOT / 'supabase/migrations/20261122000100_require_explicit_match_confirmation.sql').read_text()
work = Path(tempfile.mkdtemp(prefix='cloble-confirmation-pg-'))
socket = work / 'socket'
socket.mkdir()
env = dict(os.environ, PGHOST=str(socket), PGPORT='55440', PGUSER='postgres', PGDATABASE='postgres')


def sql(query):
    result = subprocess.run([str(BIN / 'psql'), '-X', '-qAt', '-v', 'ON_ERROR_STOP=1', '-c', query],
                            env=env, text=True, capture_output=True, timeout=20)
    assert result.returncode == 0, result.stderr
    return result.stdout.strip()


def rpc(user, function, *args):
    def literal(value):
        if isinstance(value, bool):
            return str(value).lower()
        if isinstance(value, int):
            return str(value)
        return "'" + value.replace("'", "''") + "'"
    values = ','.join(literal(arg) for arg in args)
    return json.loads(sql(f"SET test.user_id='{user}'; SELECT public.{function}({values});"))


def expect(result, expected='SUCCESS'):
    assert result['code'] == expected, result
    return result


def send(user, item, delete_pass=False):
    result = expect(rpc(user, 'rpc_send_interest', user, item, 100, delete_pass))
    assert result.get('isMutualMatch') is False and result.get('matchId') is None, result
    return result['interestId']


def respond(user, interest, item):
    return expect(rpc(user, 'rpc_respond_to_interest_and_create_match', user, interest, item, 101))['matchId']


def seed():
    sql('TRUNCATE notifications,matches,interests,swipe_passes,clothing_items,users RESTART IDENTITY CASCADE;')
    sql('''INSERT INTO users VALUES (1,'A'),(2,'B'),(3,'C');
           INSERT INTO clothing_items(id,"ownerId",name)
           SELECT u.id*100+i,u.id,'Item '||u.name||i FROM users u CROSS JOIN generate_series(1,10) i;''')


subprocess.run([str(BIN / 'initdb'), '-D', str(work / 'data'), '-A', 'trust', '-U', 'postgres', '--no-locale'],
               check=True, capture_output=True, text=True)
started = False
try:
    subprocess.run([str(BIN / 'pg_ctl'), '-D', str(work / 'data'), '-l', str(work / 'server.log'),
                    '-o', f"-k {socket} -p 55440 -c listen_addresses='' -c fsync=off -c synchronous_commit=off", '-w', 'start'],
                   check=True, capture_output=True, text=True)
    started = True
    sql(fixture)
    sql(previous)
    seed()
    expect(rpc(2, 'rpc_send_interest', 2, 101, 100))
    old = expect(rpc(1, 'rpc_send_interest', 1, 201, 101))
    assert old['isMutualMatch'] is True and sql('SELECT count(*) FROM matches') == '1'
    print('PASS: reproduces silent auto-match before the fix', flush=True)

    # CREATE OR REPLACE must retain execute grants and leave confirmation RPC unchanged.
    sql('CREATE ROLE confirmation_test_app; REVOKE EXECUTE ON FUNCTION public.rpc_send_interest(bigint,bigint,bigint,boolean) FROM PUBLIC; GRANT EXECUTE ON FUNCTION public.rpc_send_interest(bigint,bigint,bigint,boolean) TO confirmation_test_app;')
    acl_query = "SELECT proacl::text FROM pg_proc WHERE oid='public.rpc_send_interest(bigint,bigint,bigint,boolean)'::regprocedure"
    confirm_query = "SELECT pg_get_functiondef('public.rpc_respond_to_interest_and_create_match(bigint,bigint,bigint,bigint)'::regprocedure)"
    acl, confirm = sql(acl_query), sql(confirm_query)
    sql(migration)
    sql(migration)
    assert sql(acl_query) == acl and sql(confirm_query) == confirm
    assert sql('SELECT count(*) FROM matches') == '1'
    print('PASS: rerunnable migration preserves existing matches, grants and confirmation RPC', flush=True)

    seed()
    offer_b = send(2, 101)
    offer_a = send(1, 201)
    assert sql("SELECT count(*) FROM interests WHERE status='PENDING'") == '2'
    assert sql('SELECT count(*) FROM matches') == '0'
    assert sql("SELECT count(*) FROM notifications WHERE type='INCOMING_INTEREST'") == '2'
    assert sql("SELECT count(*) FROM notifications WHERE type='MATCH_VERIFIED'") == '0'
    assert sql("SELECT count(*) FROM clothing_items WHERE status='AVAILABLE'") == '30'
    print('PASS: reciprocal likes remain pending, notify both recipients, and do not reserve items', flush=True)

    match = respond(1, offer_b, 201)
    assert sql(f'SELECT status FROM interests WHERE id={offer_b}') == 'ACCEPTED_MATCHED'
    assert sql(f'SELECT status FROM interests WHERE id={offer_a}') == 'RESOLVED_BY_MATCH'
    assert sql('SELECT count(*) FROM matches') == '1'
    assert sql(f'SELECT "user1ItemId"||\':\'||"user2ItemId" FROM matches WHERE id={match}') == '101:201'
    assert sql("SELECT count(*) FROM notifications WHERE type='MATCH_VERIFIED'") == '2'
    expect(rpc(2, 'rpc_respond_to_interest_and_create_match', 2, offer_a, 102, 102), 'INTEREST_ALREADY_PROCESSED_OR_INVALID')
    assert sql('SELECT count(*) FROM matches') == '1'
    print('PASS: explicit selection creates one match, resolves the exact cross-offer and rejects stale notification actions', flush=True)

    seed()
    original_b = send(2, 101)
    original_a = send(1, 201)
    chosen = respond(2, original_a, 102)
    assert sql(f'SELECT "user1ItemId"||\':\'||"user2ItemId" FROM matches WHERE id={chosen}') == '201:102'
    assert sql(f'SELECT status FROM interests WHERE id={original_b}') == 'PENDING'
    assert sql('SELECT count(*) FROM matches WHERE "user1ItemId"=101 OR "user2ItemId"=101') == '0'
    print('PASS: receiver can explicitly choose a different item; unrelated offers remain pending', flush=True)

    seed()
    for i in range(1, 5):
        offer_a = send(1, 200 + i)
        offer_b = send(2, 100 + i)
        assert sql("SELECT count(*) FROM matches WHERE status='ACTIVE'") == str(i - 1)
        respond(2, offer_a, 100 + i)
        assert sql(f'SELECT status FROM interests WHERE id={offer_b}') == 'RESOLVED_BY_MATCH'
    assert sql("SELECT count(*) FROM matches WHERE status='ACTIVE'") == '4'
    print('PASS: four simultaneous same-partner exchanges still work, each requiring confirmation', flush=True)

    seed()
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        results = list(pool.map(lambda pair: send(*pair), [(1, 201), (2, 101)]))
    assert len(set(results)) == 2 and sql('SELECT count(*) FROM matches') == '0'
    assert sql("SELECT count(*) FROM interests WHERE status='PENDING'") == '2'
    print('PASS: simultaneous reciprocal likes cannot silently create a match', flush=True)

    seed()
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        results = list(pool.map(lambda _: rpc(1, 'rpc_send_interest', 1, 201, 100), range(2)))
    assert sorted(r['code'] for r in results) == ['ALREADY_INTERESTED', 'SUCCESS'], results
    assert sql("SELECT count(*) FROM interests WHERE status='PENDING'") == '1'
    assert sql('SELECT count(*) FROM notifications') == '1'
    print('PASS: duplicate concurrent likes create only one pending offer and one notification', flush=True)

    seed()
    expect(rpc(2, 'rpc_send_interest', 1, 201, 100), 'SENDER_NOT_FOUND')
    expect(rpc(1, 'rpc_send_interest', 1, 101, 100), 'CANNOT_INTEREST_OWN_ITEM')
    expect(rpc(1, 'rpc_send_interest', 1, 999, 100), 'TARGET_ITEM_NOT_FOUND')
    sql('UPDATE clothing_items SET "isBarterAllowed"=false WHERE id=201')
    expect(rpc(1, 'rpc_send_interest', 1, 201, 100), 'TARGET_ITEM_NOT_ELIGIBLE')
    sql('UPDATE clothing_items SET "isBarterAllowed"=false WHERE "ownerId"=1')
    expect(rpc(1, 'rpc_send_interest', 1, 202, 100), 'REQUIRES_BARTERABLE_ITEM')
    assert sql('SELECT count(*) FROM interests') == '0'
    print('PASS: session, ownership, target and barter eligibility checks stay enforced', flush=True)

    seed()
    sql('INSERT INTO swipe_passes VALUES (1,201),(1,202)')
    send(1, 201, True)
    expect(rpc(1, 'rpc_send_interest', 1, 202, 100))
    assert sql('SELECT count(*) FROM swipe_passes WHERE "targetItemId"=201') == '0'
    assert sql('SELECT count(*) FROM swipe_passes WHERE "targetItemId"=202') == '1'
    match = respond(2, int(sql('SELECT id FROM interests WHERE "targetItemId"=201')), 101)
    expect(rpc(3, 'rpc_send_interest', 3, 201, 100), 'TARGET_ITEM_NOT_ELIGIBLE')
    expect(rpc(1, 'rpc_cancel_match', match, 1, 102))
    retry = send(1, 201)
    assert sql(f'SELECT status FROM interests WHERE id={retry}') == 'PENDING'
    assert sql("SELECT count(*) FROM matches WHERE status='ACTIVE'") == '0'
    print('PASS: default arguments, pass cleanup, item reservations and retry after cancellation remain compatible', flush=True)
    print('ALL 10 EXPLICIT-CONFIRMATION REGRESSION CHECKS PASSED', flush=True)
finally:
    if started:
        subprocess.run([str(BIN / 'pg_ctl'), '-D', str(work / 'data'), '-m', 'immediate', '-w', 'stop'],
                       capture_output=True, text=True, timeout=15)
    print('Stopped disposable PostgreSQL:', work, flush=True)
