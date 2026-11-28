// `node tools/codemap/test.mjs` (no install needed)
import assert from 'node:assert/strict';
import { identifiers, summarize, users } from './codemap.mjs';

const src = `package x;

import java.util.List;

/**
 * The {@code Foo} keeper: holds <b>foos</b>. More text here.
 */
@SuppressWarnings("x")
public final class Keeper {
    // a Teams mention in a comment is no use
    /* nor Drinks here */
    private final Bar bar = new Bar();
}
`;
assert.deepEqual(summarize(src), { kind: 'class', name: 'Keeper', line: 9, doc: 'The Foo keeper: holds foos.' });
assert.equal(summarize('no type here'), null);

const ids = identifiers(src);
assert.ok(ids.has('Bar') && ids.has('List'));
assert.ok(!ids.has('Teams') && !ids.has('Drinks'), 'comments are not uses');

const sources = new Map([['a/Keeper.java', ids], ['a/Bar.java', identifiers('class Bar { Keeper k; }')], ['a/Other.java', identifiers('class Other {}')]]);
assert.deepEqual(users(sources, 'Bar', 'a/Bar.java'), ['Keeper']);
assert.deepEqual(users(sources, 'Keeper', 'a/Keeper.java'), ['Bar']);
assert.deepEqual(users(sources, 'Other', 'a/Other.java'), []);
console.log('codemap: all checks passed');
