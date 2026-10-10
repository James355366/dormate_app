const {onDocumentWritten} = require('firebase-functions/v2/firestore');
const {initializeApp} = require('firebase-admin/app');
const {getFirestore} = require('firebase-admin/firestore');
const {directoryEntry, paymentDetails} = require('./projection');
initializeApp();
exports.syncResidentFacingData = onDocumentWritten('users/{uid}', async event => {
  const db = getFirestore();
  const uid = event.params.uid;
  // Read the latest profile inside the transaction so retried/out-of-order events stay current.
  await db.runTransaction(async tx => {
    const source = await tx.get(db.doc(`users/${uid}`));
    const infoRef = db.doc(`dorm_info/${uid}`);
    const info = await tx.get(infoRef);
    const user = source.exists ? source.data() : null;
    const directory = directoryEntry(user);
    const directoryRef = db.doc(`resident_directory/${uid}`);
    if (directory) tx.set(directoryRef, directory); else tx.delete(directoryRef);
    const payment = paymentDetails(user);
    if (payment) {
      if (!info.exists || !info.data().adminName) payment.adminName = user.name || 'Dorm Admin';
      tx.set(infoRef, payment, {merge: true});
    } else if (info.exists) tx.delete(infoRef);
  });
});
