# 0005 Mail workers 2–4 with prefetch 1

SMTP is slow, so v1 runs **2–4** leased consumers (default 2), each `prefetch=1`. Uniqueness is the notification idempotency key, not a single global thread. Redis is not the mail throttle. Mailhog locally and Brevo SMTP in flag-on mode share one `NotificationSender` SMTP implementation.

**Status:** accepted
