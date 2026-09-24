# 0005 Mail workers 2–8 with prefetch 1

SMTP is slow, so v1 runs **2–8** leased consumers (default 2), each `prefetch=1`. Uniqueness is the notification idempotency key, not a single global thread. Redis is not the mail throttle. **Brevo SMTP** and the assignment stub share one `NotificationSender` SMTP implementation.

**Status:** accepted
