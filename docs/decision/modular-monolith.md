# Modular monolith

I am building **one Spring Boot process** with packages: identity, dealership, customer, vehicle, appointment, reminder, notification.

I am not splitting Appointment Service / Reminder Service / Notification Service. The assignment is one repo, one demo, one JVM I can explain. Module boundaries stay so I could extract later. Kafka, Gateway, and a mesh would be theatre at the assignment’s 50k bookings a day. I size 500k (10× the brief, not 10× the poll) with pools from CPU count because the spike is SQL + a bounded claim, not a log cluster. Why: [scale.md](scale.md).
