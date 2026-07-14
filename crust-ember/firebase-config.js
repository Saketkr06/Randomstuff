/* ────────────────────────────────────────────────────────────────
   OPTIONAL — cross-device realtime sync via Firebase Firestore.

   Leave as null  → apps sync across tabs/windows of the SAME browser.
   Paste config   → Customer app and Restaurant app connect in realtime
                    across ANY devices (customer's phone ↔ your console).

   Setup (5 min, free Spark plan):
   1. console.firebase.google.com → Add project
   2. Build → Firestore Database → Create (test mode)
   3. Project settings → Your apps → Web app → copy the config object
   4. Paste it below, redeploy. Done.
   ──────────────────────────────────────────────────────────────── */
window.CE_FIREBASE = null;

/* Example:
window.CE_FIREBASE = {
  apiKey: "AIzaSy...",
  authDomain: "crust-ember.firebaseapp.com",
  projectId: "crust-ember",
  storageBucket: "crust-ember.appspot.com",
  messagingSenderId: "1234567890",
  appId: "1:1234567890:web:abcdef"
};
*/
