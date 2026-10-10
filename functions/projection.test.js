const test = require('node:test');
const assert = require('node:assert/strict');
const {directoryEntry, paymentDetails} = require('./projection');
test('unverified, deleted and non-resident profiles are excluded', () => {
  for (const u of [null, {}, {role: 'RESIDENT', isVerified: false}, {role: 'DORM_ADMIN', isVerified: true}]) assert.equal(directoryEntry(u), null);
});
test('directory exposes only verified name and room, never email or FCM token', () => {
  assert.deepEqual(directoryEntry({role: 'RESIDENT', isVerified: true, name: 'James', roomNumber: ' 1 ', adminUid: 'admin', email: 'private', fcmToken: 'secret'}),
    {name: 'James', roomNumber: '1', adminUid: 'admin', isVerified: true});
});
test('legacy approval and verified fields are supported', () => {
  assert.equal(directoryEntry({role: 'RESIDENT', verified: true, approvedBy: 'legacy'}).adminUid, 'legacy');
});
test('payment projection excludes credentials and publishes only dorm admins', () => {
  assert.equal(paymentDetails({role: 'RESIDENT'}), null);
  assert.deepEqual(paymentDetails({role: 'DORM_ADMIN', email: 'private', gcashName: 'Admin'}), {gcashName: 'Admin', gcashNumber: '', gcashQrUrl: ''});
});
