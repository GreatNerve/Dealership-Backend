# Modular monolith

I am building **one Spring Boot process** with packages: identity, dealership, customer, vehicle, appointment, reminder, notification.

I am not splitting Appointment Service / Reminder Service / Notification Service. The assignment is one repo, one demo, one JVM I can explain. Module boundaries stay so I could extract later. Kafka, Gateway, and a mesh would be theatre at 75k bookings a day.
