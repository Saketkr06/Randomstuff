# Crust & Ember — installable Customer + Restaurant apps, connected

Two PWAs (Progressive Web Apps) sharing one live order store:
- `customer.html`   → 📱 **Customer app** (red pizza icon)
- `restaurant.html` → 🏪 **Restaurant Console app** (green icon)

## 1. Deploy (GitHub Pages)
```bash
cd site
git init && git add . && git commit -m "Crust & Ember apps"
git branch -M main
git remote add origin https://github.com/Saketkr06/crust-ember.git
git push -u origin main
```
Settings → Pages → main / root. Links:
- https://saketkr06.github.io/crust-ember/customer.html
- https://saketkr06.github.io/crust-ember/restaurant.html

## 2. Install as apps on phones
Open each link in Chrome (Android) → ⋮ menu → **Add to Home screen / Install app**.
(iPhone: Safari → Share → Add to Home Screen.)
Each installs as a separate full-screen app with its own icon — no browser bar.
The service worker also gives basic offline caching.

## 3. Connect them across devices (Firebase, ~5 min, free)
Out of the box the apps sync across tabs/windows of the **same browser** only.
For the real thing — customer orders on their phone, console rings on yours:

1. console.firebase.google.com → **Add project** (e.g. `crust-ember`)
2. Build → **Firestore Database** → Create database
3. Build → **Authentication** → Get started → enable **Anonymous**
   (optional: also enable **Google** for the "Sign in with Google" button)
4. Firestore → Rules → paste the contents of `firestore.rules` → Publish
5. Project settings ⚙️ → Your apps → **Web app** `</>` → copy `firebaseConfig`
6. Open `firebase-config.js`, replace `window.CE_FIREBASE = null;` with it
7. Commit & push. Header shows **☁️ cloud-synced · all devices**.

### Architecture in cloud mode (Domino's-style)
- **Auth**: every device signs in (anonymous by default; Google optional).
- **orders/{orderId}**: one Firestore document per order.
- **Customer app** subscribes with `where(customerId == uid)` → each customer
  sees **only their own orders**, live.
- **Restaurant console** subscribes to the whole `orders` collection
  (newest 100) → every order from every customer streams in with a bell.
- **store/inventory**: console-managed doc; stock deducts when an order is
  **accepted** and every device sees the update.
- `firestore.rules` enforces that customers can only create orders as
  themselves; a production-hardening recipe (staff custom claims) is included
  in the file.

## Redeploying updates
The service worker caches aggressively. After changing `bundle.js`, bump the
`CACHE` version string at the top of `sw.js` (v3 → v4) so installed apps pick
up the new build on next launch.

## Notes
- Payments = EmberPay **test gateway** (no real money). Swap for Razorpay checkout in production.
- Local test: `python3 -m http.server 8000` → open both pages.
