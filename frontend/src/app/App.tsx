import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useState } from 'react';
import { BrowserRouter, Link, Navigate, Route, Routes } from 'react-router-dom';
import { LoginPage } from '../features/auth/LoginPage';
import { RegisterPage } from '../features/auth/RegisterPage';
import { HOME_BY_ROLE, SessionProvider, useSession } from '../features/auth/session';
import { GuardPage } from '../features/guard/GuardPage';
import { ManagerPage } from '../features/manager/ManagerPage';
import { OwnerPage } from '../features/owner/OwnerPage';
import { StudentPage } from '../features/student/StudentPage';
import { isApiError } from '../shared/api';
import { Button } from '../shared/components/Button';
import { LoadingBlock } from '../shared/components/Spinner';
import { AppLayout } from './AppLayout';
import { RequireRole } from './RequireRole';

function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 15_000,
        refetchOnWindowFocus: false,
        // Lỗi nghiệp vụ/quyền (4xx) không thử lại; chỉ thử lại lỗi mạng hoặc 5xx
        retry: (failureCount, error) =>
          failureCount < 2 && !(isApiError(error) && error.status >= 400 && error.status < 500),
      },
      mutations: { retry: false },
    },
  });
}

export function App() {
  const [queryClient] = useState(createQueryClient);
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <SessionProvider>
          <AppRoutes />
        </SessionProvider>
      </BrowserRouter>
    </QueryClientProvider>
  );
}

function AppRoutes() {
  const { state, retry } = useSession();

  if (state.status === 'loading') {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <LoadingBlock label="Đang kiểm tra phiên đăng nhập..." />
      </div>
    );
  }
  if (state.status === 'unreachable') {
    return (
      <div className="flex min-h-screen flex-col items-center justify-center gap-4 px-4 text-center">
        <p className="text-lg font-semibold">{state.message}</p>
        <Button onClick={retry}>Thử lại</Button>
      </div>
    );
  }

  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route
        path="/"
        element={
          state.status === 'authenticated' ? <Navigate to={HOME_BY_ROLE[state.me.user.role]} replace /> : <Navigate to="/login" replace />
        }
      />
      {state.status === 'authenticated' ? (
        <Route element={<AppLayout />}>
          <Route path="/student" element={<RequireRole role="SINH_VIEN"><StudentPage /></RequireRole>} />
          <Route path="/guard" element={<RequireRole role="BAO_VE"><GuardPage /></RequireRole>} />
          <Route path="/manager" element={<RequireRole role="QUAN_LY"><ManagerPage /></RequireRole>} />
          <Route path="/owner" element={<RequireRole role="CHU_TAI_KHOAN"><OwnerPage /></RequireRole>} />
          <Route path="*" element={<NotFound />} />
        </Route>
      ) : (
        <Route path="*" element={<RequireRole role="SINH_VIEN"><NotFound /></RequireRole>} />
      )}
    </Routes>
  );
}

function NotFound() {
  return (
    <div className="mx-auto max-w-md rounded-xl bg-white p-8 text-center shadow-sm ring-1 ring-slate-200">
      <p className="text-5xl font-bold text-slate-300">404</p>
      <h1 className="mt-2 text-xl font-semibold">Không tìm thấy trang</h1>
      <Link to="/" className="mt-5 inline-block font-semibold text-blue-700 hover:underline">
        Về trang chủ
      </Link>
    </div>
  );
}
