# Security Policy

Security reports are taken seriously. If you find a vulnerability in TikFetch,
please report it privately instead of opening a public issue.

## Reporting a Vulnerability

Please send security reports to:

```text
berkeakcen@gmail.com
```

When possible, include the following details:

* A clear description of the vulnerability.
* Steps to reproduce the issue.
* The affected version, commit, branch, or deployment environment.
* Any relevant logs, screenshots, request examples, or proof of concept details.
* Whether the issue appears to affect public users, admin users, stored files,
  downloaded media, or server configuration.

Please do not include destructive payloads, real user data, private credentials,
or anything that could damage a running deployment.

## Scope

The following areas are considered security-sensitive:

* Admin authentication, sessions, JWT handling, and cookies.
* CSRF protection and form submissions.
* File download endpoints and stored media access.
* File storage paths, cleanup logic, and path traversal prevention.
* `yt-dlp` execution, configured binary paths, cookies, proxy options, and
  downloaded output handling.
* Actuator, metrics, admin pages, and other internal status endpoints.
* Rate limiting for public downloads and admin login attempts.

Reports about spam, abuse, SEO content, or non-security bugs should use the
normal GitHub issue tracker instead.

## Supported Versions

Only the latest public version of TikFetch is currently supported. If you are
running an older version, please update before reporting unless the same issue
also exists on the latest version.

## Response

After a valid report is received, the issue will be reviewed as soon as possible.
If the report is confirmed, a fix will be prepared privately and released with
credit where appropriate.

Please avoid public disclosure until a fix is available.
