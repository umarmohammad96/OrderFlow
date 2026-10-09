import { Route, Routes } from 'react-router-dom';
import Layout from './components/Layout.jsx';
import ProtectedRoute from './components/ProtectedRoute.jsx';
import AdminPage from './pages/AdminPage.jsx';
import CartPage from './pages/CartPage.jsx';
import LoginPage from './pages/LoginPage.jsx';
import NotFoundPage from './pages/NotFoundPage.jsx';
import NotificationsPage from './pages/NotificationsPage.jsx';
import OrderDetailPage from './pages/OrderDetailPage.jsx';
import OrdersPage from './pages/OrdersPage.jsx';
import ProductDetailPage from './pages/ProductDetailPage.jsx';
import ProductsPage from './pages/ProductsPage.jsx';
import RegisterPage from './pages/RegisterPage.jsx';

const guard = (el, opts) => <ProtectedRoute {...opts}>{el}</ProtectedRoute>;

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<ProductsPage />} />
        <Route path="products/:id" element={<ProductDetailPage />} />
        <Route path="login" element={<LoginPage />} />
        <Route path="register" element={<RegisterPage />} />
        <Route path="cart" element={guard(<CartPage />)} />
        <Route path="orders" element={guard(<OrdersPage />)} />
        <Route path="orders/:id" element={guard(<OrderDetailPage />)} />
        <Route path="notifications" element={guard(<NotificationsPage />)} />
        <Route path="admin" element={guard(<AdminPage />, { requireAdmin: true })} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
