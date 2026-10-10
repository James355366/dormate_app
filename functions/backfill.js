// Run once from a trusted environment with Application Default Credentials for your Firebase project.
const {initializeApp} = require('firebase-admin/app');
const {getFirestore} = require('firebase-admin/firestore');
const {directoryEntry, paymentDetails} = require('./projection');
initializeApp();
const db = getFirestore();
async function backfill() {
  const users = await db.collection('users').get();
  for (const doc of users.docs) {
    const user = doc.data();
    const directory = directoryEntry(user);
    if (directory) await db.doc(`resident_directory/${doc.id}`).set(directory);
    else await db.doc(`resident_directory/${doc.id}`).delete();
    const payment = paymentDetails(user);
    if (payment) {
      const ref = db.doc(`dorm_info/${doc.id}`);
      const existing = await ref.get();
      if (!existing.exists || !existing.data().adminName) payment.adminName = user.name || 'Dorm Admin';
      await ref.set(payment, {merge: true});
    }
  }
  console.log(`Backfilled ${users.size} profiles.`);
}
backfill().catch(e => { console.error(e.message); process.exitCode = 1; });
