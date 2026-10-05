FinTrack – Personal and Family Finance 
TrackerFinTrack is a comprehensive web application and Telegram bot designed for tracking and planning personal and family finances.   
Key Features
- Account & Transaction Management: Track incomes, expenses, and transfers across different accounts.   
- Budget Planning: Set and monitor monthly budget limits for specific expense categories.   
- Family Finances: Share financial tracking within a family using role-based access control (Owner, Member).   
- Financial Goals: Set goals and automatically calculate required monthly contributions, taking expected inflation and savings yield into account.   
- Telegram Integration: Receive automated monthly financial reports and budget exceedance notifications directly via a dedicated Telegram bot.   
Tech Stack
Backend:
- Java 21, 
- Spring Boot, 
- Spring Security, 
- Spring Data JPA (Hibernate).   
- Database: PostgreSQL (for production) and H2 (for testing), with database structure managed by Flyway migrations.   
Frontend:
- Thymeleaf, Bootstrap, Chart.js.   
Testing:
- JUnit 5, Mockito, MockMvc.   
Infrastructure:
- Docker and Docker Compose. 
