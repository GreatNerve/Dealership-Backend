# 0007 JWT with local users

Dual booking and login rate limits require identity. v1 uses Spring Security JWT, users in Postgres, roles `CUSTOMER` and `DEALERSHIP_STAFF`. No Keycloak. A `dev` profile may skip auth so the assignment curl demo stays short. Staff `dealershipId` always comes from membership, never from the create body.

**Status:** accepted
