/**
 * Auth0 Post-Login Action: "HL Budget profile claims" (research R-003).
 *
 * An access token issued for an API audience carries no profile claims, but the backend needs
 * the user's name, email and avatar to keep their record current. This Action adds them as
 * namespaced custom claims, so they are covered by the token's signature like `sub` is. Auth0
 * silently drops non-namespaced claims that collide with reserved ones, hence the namespace.
 *
 * The namespace must equal the backend's HLBUDGET_AUTH_CLAIMSNAMESPACE.
 *
 * Deploy (once per tenant, dev/test and prod; quickstart §1 step 4):
 *   1. Auth0 Dashboard → Actions → Library → Create Action → "Build from scratch".
 *      Name: "HL Budget profile claims", Trigger: "Login / Post Login", Runtime: Node 22.
 *   2. Paste this file's contents, then Deploy.
 *   3. Actions → Triggers → post-login: drag the Action into the flow between Start and
 *      Complete, then Apply.
 * Changes are made here first and pasted again; this file is the source of truth.
 */

const NAMESPACE = 'https://hl-budget/';

/**
 * @param {Event} event - Details about the user and the context in which they are logging in.
 * @param {PostLoginAPI} api - Interface whose methods can be used to change the behaviour of the login.
 */
exports.onExecutePostLogin = async (event, api) => {
  const { name, email, picture } = event.user;

  if (name) api.accessToken.setCustomClaim(`${NAMESPACE}name`, name);
  if (email) api.accessToken.setCustomClaim(`${NAMESPACE}email`, email);
  if (picture) api.accessToken.setCustomClaim(`${NAMESPACE}picture`, picture);
};
