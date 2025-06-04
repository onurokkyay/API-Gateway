# 🛡️ **API Gateway**  
### *Spring Cloud Gateway Based Microservices Entry Point*

This project is an **API Gateway** built using **Spring Cloud Gateway**. It serves as a centralized entry point for the microservices architecture, specifically designed to:

- 🔁 Route requests to the appropriate microservices (Auth Service & League Service)  
- 🔐 Validate JWT tokens for secure access control  
- 🧱 Act as a security and abstraction layer between external clients and internal services  

---

## 🔧 **Key Features**
- ✅ **JWT-based Authentication & Authorization**  
- ✅ **Dynamic Route Management**  
- ✅ **Custom Filters (Pre/Post)**  
- ✅ **Secure API Gateway Layer**  
- ✅ **Scalable Microservices Architecture**

---

## 🧩 **Microservices Behind the Gateway**

| Service           | Description |
|------------------|-------------|
| 🔐 **Auth Service**   | User registration, login, JWT generation, refresh token, role-based access |
| 🎮 **League Service** | Provides League of Legends data: match history, champion mastery, etc. |

