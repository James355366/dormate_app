// Publish minimal resident-facing data; never copy email, tokens or approval metadata.
function directoryEntry(user) {
  if (!user || user.role !== 'RESIDENT' || !(user.isVerified === true || user.verified === true)) return null;
  return {name: user.name || '', roomNumber: (user.roomNumber || '').trim(),
    adminUid: user.adminUid || user.approvedBy || '', isVerified: true};
}
function paymentDetails(user) {
  if (!user || user.role !== 'DORM_ADMIN') return null;
  return {gcashName: user.gcashName || '', gcashNumber: user.gcashNumber || '', gcashQrUrl: user.gcashQrUrl || ''};
}
module.exports = {directoryEntry, paymentDetails};
