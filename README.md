<h1 align="center">Fin</h1>

<p align="center">
  <img src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white" />
  <img src="https://img.shields.io/badge/Spring%20Boot-4.1.1-6DB33F?logo=springboot&logoColor=white" />
  <img src="https://img.shields.io/badge/React-19-20232A?logo=react&logoColor=61DAFB" />
  <img src="https://img.shields.io/badge/PostgreSQL-16%2B-316192?logo=postgresql&logoColor=white" />
</p>

<p align="center">
  A personal finance app to track your money, manage budgets, monitor savings, and gain AI-powered insights into your financial activity.
</p>

### Features

- **Transactions** — Track and categorize income and expenses.
- **Dashboard** — Get an overview of your finances, spending trends, and top expenses.
- **Budgets** — Set monthly category budgets and monitor your spending.
- **Savings Goals** — Create financial goals and track your progress.
- **Recurring Transactions** — Schedule transactions and forecast upcoming cash flow.
- **AI Financial Assistant** — Ask questions and get insights based on your financial data.
- **Export & Notifications** — Export transactions to CSV and receive notifications (FCM).
- **Account & Payments** — Manage your account, email verification, and subscriptions.

### Tech Stack

| Category      | Technologies                          |
|---------------|---------------------------------------|
| Frontend      | React, TypeScript, Vite, Tailwind CSS |
| Backend       | Java 25, Spring Boot, Spring AI       |
| Database      | PostgreSQL 16+, pgvector              |
| Caching       | Redis                                 |
| Migrations    | Flyway                                |
| Notifications | Firebase Cloud Messaging (FCM)        |

### Getting Started

**Prerequisites** Docker, Java 25, Node.js 22+, npm

**1. Clone the repository**

```bash
git clone <repository-url>
cd Fin
```

**2. Start infrastructure**

Start PostgreSQL and Redis using Docker Compose:

```bash
docker compose -f docker/compose/dev/compose.yaml up -d
```

**3. Configure the backend**

Set up your backend environment variables using `backend/.env.example`.

Configure the required credentials and services:

- OpenAI API key for AI-powered features
- Mail service credentials for email functionality
- Razorpay credentials for subscription payments
- Firebase Cloud Messaging credentials for notifications

Run the backend:

```bash
cd backend
./mvnw spring-boot:run
```

### 4. Configure the frontend

Create your frontend environment file:

```bash
cp frontend/.env.example frontend/.env
```

Install dependencies and start the development server:

```bash
cd frontend
npm install
npm run dev
```

## Local URLs

| Service           | URL                                       |
|-------------------|-------------------------------------------|
| Frontend          | http://localhost:5173                     |
| Backend API       | http://localhost:8080                     |
| API Documentation | http://localhost:8080/api/swagger-ui.html |


> Make sure all required environment variables and credentials are configured correctly before running the application.

> Some features, such as AI chat, email, payments, and notifications, require additional configuration.

## Contributing

Contributions are always welcome!

Have an idea for a feature, improvement, bug fix, or a better way to do something? Feel free to open an issue or submit
a pull request.

You don't need to have everything figured out — even small improvements make a difference. Build something, experiment,
and have fun with it. Vibe coding is welcome too!

If you have an idea in mind, let's make it happen.

---
Connect with me:

- LinkedIn - https://linkedin.com/in/ripanbaidya
- Mail - connect.ripanbaidya@gmail.com