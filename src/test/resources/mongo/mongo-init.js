//db = db.getSiblingDB('test');
//
//var rootUser = '$MONGO_INITDB_ROOT_USERNAME';
//var rootPassword = '$MONGO_INITDB_ROOT_PASSWORD';
//
//db.createCollection('Brands');
//
//db.Brands.insertMany([
// {
//   "uid": "test-brand",
//   "network": "test",
//   "connectorUid": "mock-connector",
//   "deleted": false,
//   "tenant": "default",
//   "status": "ACTIVE",
//   "currency": "EUR",
//   "demoBalance": 1000.0
// }
//]);
//
//
//db.createCollection('Games');
//
//db.Games.insertMany([
//{
//  "uid": "blackjack",
//  "baseUrl": "",
//  "deleted": false,
//  "tenant": "default"
//}
//]);
//
//
//db.createCollection('BrandGames');
//
//db.BrandGames.insertMany([
// {
//   "brand": "test-brand",
//   "network": "test",
//   "game": "blackjack",
//   "status": "ACTIVE",
//   "deleted": false,
//   "tenant": "default"
// }
//]);