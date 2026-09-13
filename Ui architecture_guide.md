# UI/Frontend Architecture for E-Commerce Microservices

## Angular vs. Next.js Comparison

### Quick Recommendation
**Use Next.js 14 for customer-facing storefront + React admin dashboard** (or separate admin with Angular if team expertise exists)

---

## Detailed Comparison

### 1. **Next.js 14 (App Router)** ✅ RECOMMENDED for Storefront

#### **Pros**
- ✅ **SEO-Friendly**: Server-side rendering (SSR) by default, static generation (SSG) for product pages
  - Product catalog, search results indexed by Google
  - Meta tags, Open Graph for social sharing
- ✅ **Modern React Ecosystem**: Latest React 18+ hooks, concurrent features
- ✅ **API Routes**: Can build lightweight proxy endpoints in frontend (bypass CORS issues)
- ✅ **File-based Routing**: Intuitive URL structure (`app/products/[id]/page.tsx` → `/products/:id`)
- ✅ **Built-in Optimizations**:
  - Image optimization (`<Image>` component)
  - Font loading optimization
  - Code splitting (automatic per route)
  - Streaming (render UI while fetching data)
- ✅ **ISR (Incremental Static Regeneration)**: Update product pages without full rebuild
- ✅ **Faster Development**: Less boilerplate than Angular
- ✅ **Vercel Hosting**: Seamless deployment, serverless functions, CDN
- ✅ **Real-time Ready**: WebSocket support via Next.js API routes or external services

#### **Cons**
- ❌ **Not a Full Framework**: Requires picking libraries for state management (Redux, Zustand, Jotai)
- ❌ **Learning Curve on SSR**: Hydration mismatches, server vs. client code separation
- ❌ **Admin Dashboard**: Overkill for SSR; better to use separate SPA
- ❌ **Authentication Complexity**: NextAuth.js needed for OAuth2 (adds layer)
- ❌ **Smaller Enterprise Community**: More startup-focused than Angular

#### **Best For**
- ✅ E-commerce product storefront (high SEO value)
- ✅ Public product catalog, search, filtering
- ✅ Product detail pages (dynamic rendering with ISR)
- ✅ User account pages (protected routes)
- ✅ Shopping cart & checkout flow

---

### 2. **Angular 17+** ❌ NOT RECOMMENDED (but viable if team expertise exists)

#### **Pros**
- ✅ **Full-Featured Framework**: Everything included (routing, state management, forms, HTTP)
- ✅ **Strong Typing**: TypeScript-first, strict mode by default
- ✅ **Dependency Injection**: Built-in, proven pattern for testing
- ✅ **NgRx/Akita**: Mature state management solutions
- ✅ **RxJS**: Reactive programming built-in (powerful for real-time updates)
- ✅ **Enterprise Adoption**: Large teams, proven at scale
- ✅ **CLI Tooling**: `ng generate` scaffolding reduces boilerplate
- ✅ **Forms**: Reactive Forms (powerful validation, dynamic form building)
- ✅ **Admin Dashboard**: Excellent for complex admin UIs (RBAC, data tables, charts)

#### **Cons**
- ❌ **SEO Challenges**: SSR requires extra setup (Angular Universal)
  - Product pages may not rank well without server-side rendering
  - Dynamic meta tags harder to manage
  - Larger JavaScript bundle → slower initial load
- ❌ **Steep Learning Curve**: Decorators, RxJS, dependency injection, zones
  - Higher onboarding time for new developers
  - More complexity than modern React
- ❌ **Bundle Size**: ~500KB+ (minified), vs. Next.js ~150KB
- ❌ **Slower Development**: More boilerplate (e.g., service + interface + component + module)
- ❌ **Overkill for Storefront**: Designed for large SPA admin apps, not content-heavy sites
- ❌ **Real-time Updates**: Requires manual WebSocket setup (vs. Next.js streaming)

#### **Best For**
- ✅ Complex admin dashboards (user management, order management, analytics)
- ✅ Real-time data grids and dynamic UIs
- ✅ Large enterprise SPA (if SEO not critical)
- ✅ Teams with existing Angular expertise

---

### 3. **React 18 + Vite** (Alternative Middle Ground)

#### **Pros**
- ✅ Same React ecosystem as Next.js
- ✅ Simpler setup than Next.js (full SPA)
- ✅ Fast dev server (Vite)
- ✅ Flexible: add state management as needed

#### **Cons**
- ❌ No SSR out-of-the-box (need SSR setup for SEO)
- ❌ No built-in API routes
- ❌ Requires more decisions (routing library, state management, etc.)

#### **Use Case**
- Admin dashboard (if not using Angular)
- Mobile app shell (React Native)

---

## Recommended Architecture

### **Hybrid Approach** (Best for E-Commerce)

```
┌─────────────────────────────────────────────────────────────┐
│                     CDN / Frontend                          │
├──────────────────────────┬──────────────────────────────────┤
│   Next.js 14 Storefront  │  Admin Dashboard (React/Angular) │
│   (Vercel)               │  (Separate SPA, AWS/Netlify)     │
│                          │                                  │
│ • Public product pages   │ • Order management               │
│ • Search & filtering     │ • User management                │
│ • User accounts          │ • Inventory control              │
│ • Shopping cart          │ • Analytics & reporting          │
│ • Checkout               │ • Content moderation             │
└──────────────────────────┴──────────────────────────────────┘
                         │
            ┌────────────┴────────────┐
            │    API Gateway          │
            │   (Spring Cloud         │
            │    Gateway)             │
            │   :8080                 │
            └────────────┬────────────┘
                         │
        ┌────────────────┼────────────────┐
        │                │                │
    ┌───────────┐  ┌─────────────┐  ┌──────────────┐
    │   User    │  │  Product    │  │    Order     │
    │  Service  │  │  Service    │  │   Service    │
    │           │  │             │  │              │
    │ JWT/OAuth2│  │ Cache/Redis │  │ Kafka/Events │
    └───────────┘  └─────────────┘  └──────────────┘
```

### **Why This Works**
1. **Next.js Storefront**:
   - SSR/SSG for SEO (product pages rank in Google)
   - Fast perceived performance (streaming, image optimization)
   - Real-time updates via WebSocket (cart, notifications)
   - Handles 80% of traffic (public browsing)

2. **Separate Admin Dashboard**:
   - Doesn't need SEO (internal tool)
   - Can be more complex UI (data grids, charts, RBAC)
   - Separate deployment cadence from storefront
   - Team can use framework they prefer (React SPA or Angular)

3. **Shared Backend**:
   - API Gateway routes `/api/**` to services
   - CORS configured for both frontends
   - Unified authentication (JWT from user-service)

---

## Detailed Frontend Specifications

### **A. Next.js Storefront** (Customer-Facing)

#### **Tech Stack**
```json
{
  "runtime": "Node.js 20+ LTS",
  "framework": "Next.js 14.x (App Router)",
  "language": "TypeScript",
  "styling": "Tailwind CSS + Shadcn/UI",
  "state-management": "Zustand or TanStack Query (React Query)",
  "auth": "NextAuth.js v5 + JWT",
  "api-client": "Axios with interceptors",
  "forms": "React Hook Form + Zod",
  "real-time": "Socket.io or native WebSocket",
  "testing": "Vitest + React Testing Library",
  "e2e": "Playwright",
  "ui-components": "Shadcn/UI or Headless UI",
  "icons": "Lucide React",
  "package-manager": "pnpm"
}
```

#### **Project Structure**
```
storefront/
├── app/                                    (App Router - Next.js 13+)
│   ├── (auth)/
│   │   ├── login/page.tsx
│   │   ├── register/page.tsx
│   │   └── oauth/callback/page.tsx
│   ├── (shop)/
│   │   ├── products/
│   │   │   ├── page.tsx                   (Product listing + search)
│   │   │   ├── [id]/
│   │   │   │   ├── page.tsx              (Product detail)
│   │   │   │   └── layout.tsx
│   │   │   └── search/page.tsx           (Search results)
│   │   ├── cart/page.tsx
│   │   ├── checkout/page.tsx
│   │   ├── orders/
│   │   │   ├── page.tsx                  (Order history)
│   │   │   └── [id]/page.tsx             (Order detail)
│   │   └── account/
│   │       ├── profile/page.tsx
│   │       ├── addresses/page.tsx
│   │       └── settings/page.tsx
│   ├── api/                               (API routes / proxies)
│   │   ├── auth/[...nextauth]/route.ts   (NextAuth.js endpoints)
│   │   ├── products/route.ts             (Optional: proxy to backend)
│   │   └── orders/route.ts
│   ├── layout.tsx                        (Root layout)
│   ├── page.tsx                          (Homepage)
│   └── error.tsx, not-found.tsx          (Error boundaries)
├── components/
│   ├── ProductCard.tsx
│   ├── CartSidebar.tsx
│   ├── Navbar.tsx
│   ├── Footer.tsx
│   └── common/
│       ├── Button.tsx
│       ├── Form.tsx
│       └── ...
├── lib/
│   ├── api.ts                            (Axios instance with JWT interceptor)
│   ├── auth.ts                           (NextAuth.js config)
│   ├── store.ts                          (Zustand store)
│   ├── hooks.ts                          (Custom hooks)
│   └── utils.ts
├── styles/
│   └── globals.css                       (Tailwind + global styles)
├── public/
│   └── images/, icons/, etc.
├── .env.local                            (API_BASE_URL, OAUTH keys, etc.)
├── package.json
├── tsconfig.json
├── next.config.js
├── tailwind.config.js
└── eslint.config.js
```

#### **Key Features**

**1. Authentication Flow**
```typescript
// lib/auth.ts
import NextAuth from "next-auth";
import CredentialsProvider from "next-auth/providers/credentials";
import GoogleProvider from "next-auth/providers/google";

export const authOptions = {
  providers: [
    CredentialsProvider({
      name: "Email & Password",
      credentials: {
        email: { label: "Email", type: "email" },
        password: { label: "Password", type: "password" }
      },
      async authorize(credentials) {
        // Call backend: POST /api/users/login
        const response = await fetch(`${process.env.API_BASE_URL}/api/users/login`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            email: credentials?.email,
            password: credentials?.password
          })
        });
        
        if (!response.ok) throw new Error("Invalid credentials");
        
        return response.json(); // { accessToken, refreshToken, user }
      }
    }),
    GoogleProvider({
      clientId: process.env.GOOGLE_CLIENT_ID || "",
      clientSecret: process.env.GOOGLE_CLIENT_SECRET || "",
      authorization: {
        params: {
          redirect_uri: `${process.env.NEXTAUTH_URL}/auth/oauth/google/callback`
        }
      }
    })
  ],
  callbacks: {
    async jwt({ token, user, account }) {
      if (user) {
        token.accessToken = user.accessToken;
        token.refreshToken = user.refreshToken;
        token.id = user.id;
      }
      return token;
    },
    async session({ session, token }) {
      session.user = {
        ...session.user,
        id: token.id as string,
        accessToken: token.accessToken as string
      };
      return session;
    }
  },
  pages: {
    signIn: "/login",
    error: "/auth/error"
  }
};
```

**2. API Client with JWT Interceptor**
```typescript
// lib/api.ts
import axios from "axios";
import { getSession } from "next-auth/react";

const apiClient = axios.create({
  baseURL: process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080"
});

// Add JWT token to all requests
apiClient.interceptors.request.use(async (config) => {
  const session = await getSession();
  if (session?.user?.accessToken) {
    config.headers.Authorization = `Bearer ${session.user.accessToken}`;
  }
  
  // Add correlation ID
  config.headers["X-Correlation-ID"] = `frontend-${Date.now()}`;
  
  return config;
});

// Handle token refresh on 401
apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (error.response?.status === 401) {
      // Call refresh endpoint
      // Redirect to login if refresh fails
    }
    return Promise.reject(error);
  }
);

export default apiClient;
```

**3. State Management (Zustand)**
```typescript
// lib/store.ts
import { create } from "zustand";

interface CartItem {
  productId: string;
  name: string;
  price: number;
  quantity: number;
}

interface CartStore {
  items: CartItem[];
  totalPrice: number;
  addItem: (item: CartItem) => void;
  removeItem: (productId: string) => void;
  updateQuantity: (productId: string, quantity: number) => void;
  clear: () => void;
}

export const useCartStore = create<CartStore>((set) => ({
  items: [],
  totalPrice: 0,
  
  addItem: (item) => set((state) => {
    const existing = state.items.find(i => i.productId === item.productId);
    const newItems = existing
      ? state.items.map(i => i.productId === item.productId
          ? { ...i, quantity: i.quantity + item.quantity }
          : i)
      : [...state.items, item];
    
    const totalPrice = newItems.reduce((sum, i) => sum + (i.price * i.quantity), 0);
    return { items: newItems, totalPrice };
  }),
  
  removeItem: (productId) => set((state) => {
    const newItems = state.items.filter(i => i.productId !== productId);
    const totalPrice = newItems.reduce((sum, i) => sum + (i.price * i.quantity), 0);
    return { items: newItems, totalPrice };
  }),
  
  updateQuantity: (productId, quantity) => set((state) => {
    const newItems = state.items.map(i =>
      i.productId === productId ? { ...i, quantity } : i
    );
    const totalPrice = newItems.reduce((sum, i) => sum + (i.price * i.quantity), 0);
    return { items: newItems, totalPrice };
  }),
  
  clear: () => set({ items: [], totalPrice: 0 })
}));
```

**4. Product Search with TanStack Query**
```typescript
// app/(shop)/products/page.tsx
"use client";

import { useQuery } from "@tanstack/react-query";
import { useSearchParams } from "next/navigation";
import apiClient from "@/lib/api";

export default function ProductsPage() {
  const searchParams = useSearchParams();
  const query = searchParams.get("q") || "";
  const category = searchParams.get("category") || "";
  const page = parseInt(searchParams.get("page") || "1");
  
  const { data, isLoading, error } = useQuery({
    queryKey: ["products", query, category, page],
    queryFn: async () => {
      const response = await apiClient.post("/api/products/search", {
        query,
        category,
        page,
        pageSize: 20
      });
      return response.data;
    }
  });
  
  if (isLoading) return <div>Loading...</div>;
  if (error) return <div>Error loading products</div>;
  
  return (
    <div>
      <h1>Products</h1>
      <div className="grid grid-cols-4 gap-4">
        {data.items.map((product) => (
          <ProductCard key={product.id} product={product} />
        ))}
      </div>
      {/* Pagination */}
    </div>
  );
}
```

**5. Real-Time Notifications (WebSocket)**
```typescript
// lib/websocket.ts
import { useEffect } from "react";

export function useNotifications(userId: string) {
  useEffect(() => {
    const ws = new WebSocket(`${process.env.NEXT_PUBLIC_WS_URL}/notifications/${userId}`);
    
    ws.onmessage = (event) => {
      const notification = JSON.parse(event.data);
      
      if (notification.type === "OrderCreated") {
        console.log("Order confirmation:", notification.orderId);
        // Show toast notification
      } else if (notification.type === "OrderStatusChanged") {
        console.log("Order updated:", notification.status);
        // Refresh order status
      }
    };
    
    return () => ws.close();
  }, [userId]);
}
```

#### **Deployment (Vercel)**
```yaml
# .env.production
NEXT_PUBLIC_API_BASE_URL=https://api.example.com
NEXTAUTH_URL=https://storefront.example.com
NEXTAUTH_SECRET=<secure-random-string>
GOOGLE_CLIENT_ID=<from-google-console>
GOOGLE_CLIENT_SECRET=<from-google-console>
```

**Deploy via Vercel CLI or Git integration:**
```bash
vercel deploy --prod
```

---

### **B. Admin Dashboard** (Internal Tool)

#### **Option 1: React SPA** (Recommended if no Angular expertise)

**Tech Stack**
```json
{
  "framework": "React 18 + Vite",
  "language": "TypeScript",
  "state-management": "Redux Toolkit",
  "routing": "React Router v6",
  "ui-library": "Ant Design or Material-UI",
  "charts": "Recharts or Chart.js",
  "api-client": "Axios",
  "auth": "JWT + React Router protected routes",
  "testing": "Vitest + React Testing Library"
}
```

**Structure**
```
admin/
├── src/
│   ├── App.tsx
│   ├── pages/
│   │   ├── Login.tsx
│   │   ├── Dashboard.tsx
│   │   ├── Orders/
│   │   │   ├── OrderList.tsx
│   │   │   ├── OrderDetail.tsx
│   │   │   └── OrderForm.tsx
│   │   ├── Users/
│   │   │   ├── UserList.tsx
│   │   │   └── UserForm.tsx
│   │   ├── Products/
│   │   │   ├── ProductList.tsx
│   │   │   ├── ProductForm.tsx
│   │   │   └── ProductImageUpload.tsx
│   │   └── Analytics/
│   │       ├── SalesChart.tsx
│   │       └── UserMetrics.tsx
│   ├── components/
│   │   ├── Table.tsx              (Reusable data table)
│   │   ├── Form.tsx               (Dynamic forms)
│   │   ├── ConfirmDialog.tsx
│   │   └── ProtectedRoute.tsx      (JWT validation)
│   ├── store/                      (Redux)
│   │   ├── slices/authSlice.ts
│   │   ├── slices/ordersSlice.ts
│   │   └── index.ts
│   └── hooks/
│       └── useAuth.ts
├── package.json
└── vite.config.ts
```

#### **Option 2: Angular** (If team expertise exists)

**Structure**
```
admin/
├── src/
│   ├── app/
│   │   ├── app.module.ts
│   │   ├── app-routing.module.ts
│   │   ├── auth/
│   │   │   ├── login/login.component.ts
│   │   │   └── auth.guard.ts
│   │   ├── dashboard/
│   │   │   └── dashboard.component.ts
│   │   ├── orders/
│   │   │   ├── order-list/order-list.component.ts
│   │   │   ├── order-detail/order-detail.component.ts
│   │   │   └── orders.service.ts
│   │   ├── products/
│   │   │   ├── product-list/product-list.component.ts
│   │   │   ├── product-form/product-form.component.ts
│   │   │   └── products.service.ts
│   │   ├── users/
│   │   │   ├── user-list/user-list.component.ts
│   │   │   └── users.service.ts
│   │   └── shared/
│   │       ├── interceptors/jwt.interceptor.ts
│   │       ├── guards/auth.guard.ts
│   │       └── models/
│   └── main.ts
├── angular.json
└── package.json
```

#### **Deployment**
- **React SPA**: Netlify or AWS S3 + CloudFront
- **Angular**: Firebase Hosting or Netlify

```bash
# React
npm run build
netlify deploy --prod --dir=dist

# Angular
ng build --configuration production
firebase deploy --only hosting
```

---

## Architecture Decision Matrix

| Requirement | Next.js Storefront | React Admin | Angular Admin |
|-------------|-------------------|------------|---------------|
| **SEO** | ✅ Excellent (SSR) | ❌ Limited (SPA) | ❌ Limited (SPA) |
| **Performance** | ✅ Fast (ISR, streaming) | ✅ Good (SPA, optimized) | ⚠️ Slower bundle |
| **Time to Market** | ✅ Fast | ✅ Fast | ❌ Slower |
| **Learning Curve** | ✅ Moderate | ✅ Moderate | ❌ Steep |
| **Real-time Updates** | ✅ Good | ✅ Good | ✅ Good |
| **Complex Admin UI** | ❌ Not ideal | ✅ Good | ✅ Excellent |
| **Data Tables** | ❌ Overkill | ✅ Good (with libs) | ✅ Excellent |
| **Existing Skills** | → Check team | → Check team | → Check team |
| **Maintenance** | ✅ Easy (community) | ✅ Easy (React) | ⚠️ Harder (learning) |

---

## Cross-Cutting Concerns

### **Authentication Flow** (Both Frontends)

```
User logs in (Next.js Storefront)
        ↓
Next-auth redirects to backend: /auth/users/login (POST)
        ↓
Backend returns: { accessToken (15 min), refreshToken (7 days), user }
        ↓
Next-auth stores in JWT + httpOnly cookie
        ↓
All API calls: Authorization: Bearer <accessToken>
        ↓
On 401 (token expired): Refresh via /auth/users/refresh
        ↓
If refresh fails: Redirect to /login
```

### **API Rate Limiting** (At Gateway)

Both frontends hit the same API Gateway:
- 100 requests/min per IP (unauthenticated)
- 1000 requests/min per user (authenticated via JWT)

**Handling 429 (Too Many Requests)**:
```typescript
// lib/api.ts (Next.js example, same in React)
if (error.response?.status === 429) {
  const retryAfter = error.response.headers["retry-after"];
  setTimeout(() => retry(), retryAfter * 1000);
}
```

### **Error Handling** (Standardized)

Backend returns:
```json
{
  "code": "ERR_INSUFFICIENT_STOCK",
  "message": "Product out of stock",
  "details": {
    "productId": "123",
    "available": 0,
    "requested": 5
  }
}
```

Frontend handles globally:
```typescript
// lib/errorHandler.ts
export function handleApiError(error: AxiosError<ApiError>) {
  switch (error.response?.data?.code) {
    case "ERR_INSUFFICIENT_STOCK":
      showToast("Out of stock", "error");
      break;
    case "ERR_UNAUTHORIZED":
      redirectToLogin();
      break;
    default:
      showToast(error.response?.data?.message || "Unknown error", "error");
  }
}
```

### **Correlation IDs** (Tracing)

Every frontend request includes:
```typescript
headers: {
  "X-Correlation-ID": `frontend-${Date.now()}-${Math.random()}`
}
```

Backend logs with same correlation ID → can trace full request flow in logs.

---

## Recommendation Summary

### ✅ **Go With:**
1. **Next.js 14 (App Router)** for storefront
   - Better SEO for product pages
   - Faster time-to-market
   - Modern React patterns
   - Vercel deployment is seamless

2. **React 18 + Vite** for admin dashboard
   - Simpler than Angular
   - Sufficient for CRUD operations
   - Faster to develop
   - Same team can maintain both

### ❌ **Avoid:**
- Single monolithic frontend (Next.js + admin together)
- Angular for storefront (SEO penalty, overkill)
- Angular unless team has existing expertise

### ✅ **Use Angular IF:**
- Team is already skilled in Angular
- Admin dashboard has very complex UIs (thousands of real-time data points)
- Enterprise standards require full SPA framework

---

## Deployment Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        CDN (Cloudflare)                     │
├──────────────────────────┬──────────────────────────────────┤
│   Vercel (Next.js)       │   Netlify (React Admin)          │
│   storefront.example.com │   admin.example.example.com      │
└──────────────────────────┴──────────────────────────────────┘
                         │
            ┌────────────┴────────────┐
            │                        │
      AWS CloudFront          (CORS: admin.example.com)
      api.example.com
            │
    ┌───────┴────────┐
    │ API Gateway    │
    │ :8080          │
    └────────────────┘
```

**Environment Variables Per Frontend**

`.env.local` (Next.js Storefront):
```
NEXT_PUBLIC_API_BASE_URL=https://api.example.com
NEXT_PUBLIC_WS_URL=wss://ws.example.com
NEXTAUTH_URL=https://storefront.example.com
NEXTAUTH_SECRET=<secure>
GOOGLE_CLIENT_ID=<from-google-console>
GOOGLE_CLIENT_SECRET=<from-google-console>
```

`.env.local` (React Admin):
```
VITE_API_BASE_URL=https://api.example.com
VITE_APP_ENV=production
```

---

## Conclusion

**For this e-commerce microservices project:**

| Component | Framework | Reasoning |
|-----------|-----------|-----------|
| **Storefront** | Next.js 14 | SEO, performance, modern React |
| **Admin Dashboard** | React 18 + Vite | Simplicity, maintainability, speed |
| **Mobile App** | React Native (future) | Code sharing with web (React ecosystem) |

Both share backend API via Gateway, both use JWT auth, both support real-time updates via WebSocket/Kafka.