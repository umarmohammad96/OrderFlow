# OrderFlow Architecture

React Client
     |
     v
REST API
     |
     v
Spring Boot Application
     |
     +--> Auth Module
     +--> Product Module
     +--> Inventory Module
     +--> Cart Module
     +--> Order Module
     +--> Payment Module
     +--> Notification Module
     |
     v
Service Layer
     |
     v
Repository Layer
     |
     v
PostgreSQL

External Infrastructure:
- Redis — Caching
- RabbitMQ — Asynchronous Events