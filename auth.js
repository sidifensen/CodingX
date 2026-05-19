require("./chunk.js");
const require_index = require("./index.js");
require_index.init_auth();
exports.discoverAuthorizationServerMetadata = require_index.discoverAuthorizationServerMetadata;
exports.discoverOAuthProtectedResourceMetadata = require_index.discoverOAuthProtectedResourceMetadata;
exports.exchangeAuthorization = require_index.exchangeAuthorization;
exports.refreshAuthorization = require_index.refreshAuthorization;
