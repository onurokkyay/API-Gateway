# 🛡️ **API Gateway**  
### *Reactive Spring Cloud Gateway Based Microservices Entry Point*

This project is an **API Gateway** built using **Spring Cloud Gateway (Reactive)**. It serves as a centralized entry point for the microservices architecture, specifically designed to:

- 🔁 Route requests to the appropriate microservices (Auth Service & League Service)  
- 🔐 Validate JWT tokens for secure and reactive access control  
- 🧱 Act as a secure abstraction layer between external clients and internal services  

---

## 🔧 **Key Features**
- ✅ **JWT-based Authentication & Authorization with Reactive Security**  
- ✅ **Dynamic Route Management with Predicates and Filters**  
- ✅ **Custom Pre/Post Gateway Filters**  
- ✅ **Secure, Scalable, and Non-blocking API Gateway Layer**  
- ✅ **Designed for Reactive Microservices Architectures**

---

## 🧩 **Microservices Behind the Gateway**

| Service           | Description |
|-------------------|-------------|
| 🔐 **Auth Service**   | User registration, login, JWT generation, refresh token, role-based access control |
| 🎮 **League Service** | Provides League of Legends data: match history, champion mastery, summoner info, etc. |

## 📖 **Swagger UI**

### Auth Service:
`http://localhost:8080/api/auth/swagger-ui.html`
### League Service:
`http://localhost:8080/api/league/swagger-ui.html`


---
