'use strict';

/**
 * Role-based access control (§2).
 *
 * The requirements name six user groups, and they are not interchangeable: an accounts clerk has no
 * business changing a government filing status, and only a team leader should see the whole team's
 * location history.
 *
 * Permissions are issued by the server and returned in the session. The app hides what is absent,
 * but hiding a button is not authorisation - every endpoint checks the permission itself. An app can
 * be modified; this list cannot.
 */

const P = {
  CLIENTS_READ: 'clients:read',
  CLIENTS_STATUS: 'clients:status',
  CLIENTS_REMARK: 'clients:remark',
  CLIENTS_DOCUMENTS: 'clients:documents',
  CLIENTS_PAYMENT: 'clients:payment',
  CLIENTS_GOVERNMENT: 'clients:government',
  CALLS_PLACE: 'calls:place',
  CALLS_HISTORY: 'calls:history',
  ATTENDANCE_WRITE: 'attendance:write',
  TEAM_VIEW: 'team:view',
  ADMIN_CONSOLE: 'admin:console',
};

/**
 * What each group may do.
 *
 * Deliberately narrow. Documentation staff cannot mark a payment received; accounts cannot move a
 * government application forward. Those separations are the point of having roles at all.
 */
const ROLE_PERMISSIONS = {
  CUSTOMER_SUPPORT: [
    P.CLIENTS_READ, P.CLIENTS_STATUS, P.CLIENTS_REMARK,
    P.CALLS_PLACE, P.CALLS_HISTORY, P.ATTENDANCE_WRITE,
  ],
  SALES: [
    P.CLIENTS_READ, P.CLIENTS_STATUS, P.CLIENTS_REMARK,
    P.CALLS_PLACE, P.CALLS_HISTORY, P.ATTENDANCE_WRITE,
  ],
  DOCUMENTATION: [
    P.CLIENTS_READ, P.CLIENTS_STATUS, P.CLIENTS_REMARK,
    P.CLIENTS_DOCUMENTS, P.CLIENTS_GOVERNMENT,
    P.CALLS_HISTORY, P.ATTENDANCE_WRITE,
  ],
  ACCOUNTS: [
    P.CLIENTS_READ, P.CLIENTS_REMARK, P.CLIENTS_PAYMENT,
    P.CALLS_HISTORY, P.ATTENDANCE_WRITE,
  ],
  RELATIONSHIP_MANAGER: [
    P.CLIENTS_READ, P.CLIENTS_STATUS, P.CLIENTS_REMARK,
    P.CLIENTS_DOCUMENTS, P.CLIENTS_PAYMENT,
    P.CALLS_PLACE, P.CALLS_HISTORY, P.ATTENDANCE_WRITE,
  ],
  TEAM_LEADER: [
    P.CLIENTS_READ, P.CLIENTS_STATUS, P.CLIENTS_REMARK,
    P.CLIENTS_DOCUMENTS, P.CLIENTS_PAYMENT, P.CLIENTS_GOVERNMENT,
    P.CALLS_PLACE, P.CALLS_HISTORY, P.ATTENDANCE_WRITE,
    P.TEAM_VIEW, P.ADMIN_CONSOLE,
  ],
};

/** Which statuses each role may set. A quick action the role cannot use is not shown. */
const ROLE_STATUSES = {
  CUSTOMER_SUPPORT: ['DOCUMENTS_RECEIVED', 'CLIENT_NOT_RESPONDING', 'CALLBACK_SCHEDULED'],
  SALES: ['CLIENT_NOT_RESPONDING', 'CALLBACK_SCHEDULED', 'COMPLETED'],
  DOCUMENTATION: ['DOCUMENTS_RECEIVED', 'WAITING_GOVERNMENT_APPROVAL', 'COMPLETED'],
  ACCOUNTS: ['PAYMENT_RECEIVED'],
  RELATIONSHIP_MANAGER: [
    'DOCUMENTS_RECEIVED', 'CLIENT_NOT_RESPONDING', 'PAYMENT_RECEIVED',
    'WAITING_GOVERNMENT_APPROVAL', 'COMPLETED', 'CALLBACK_SCHEDULED',
  ],
  TEAM_LEADER: [
    'DOCUMENTS_RECEIVED', 'CLIENT_NOT_RESPONDING', 'PAYMENT_RECEIVED',
    'WAITING_GOVERNMENT_APPROVAL', 'COMPLETED', 'CALLBACK_SCHEDULED',
  ],
};

const permissionsFor = (role) => ROLE_PERMISSIONS[role] || ROLE_PERMISSIONS.CUSTOMER_SUPPORT;
const statusesFor = (role) => ROLE_STATUSES[role] || [];
const can = (employee, permission) => (employee.permissions || []).includes(permission);

/** Express guard. Returns 403 with the permission named, so a denial is debuggable. */
function require_(permission) {
  return (req, res, next) => {
    if (!can(req.employee, permission)) {
      return res.status(403).json({
        code: 'FORBIDDEN',
        message: 'Your role does not allow this action.',
        fieldErrors: { permission },
      });
    }
    return next();
  };
}

module.exports = { P, ROLE_PERMISSIONS, ROLE_STATUSES, permissionsFor, statusesFor, can, require: require_ };
