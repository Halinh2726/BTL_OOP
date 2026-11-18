import { useState } from 'react';
import { Alert } from '../../shared/components/Alert';
import { Button } from '../../shared/components/Button';
import { Dialog } from '../../shared/components/Dialog';
import type { TemporaryPasswordResult } from '../../shared/types/api';

/**
 * Hiện mật khẩu tạm ĐÚNG MỘT LẦN để quản lý đọc / đưa cho người dùng. Hệ thống chỉ lưu hash nên đóng hộp thoại là
 * không xem lại được — quên thì cấp lại mật khẩu tạm mới.
 */
export function TemporaryPasswordDialog({ result, onClose }: { result: TemporaryPasswordResult; onClose: () => void }) {
  const [copied, setCopied] = useState(false);

  async function copy() {
    try {
      await navigator.clipboard.writeText(result.temporaryPassword);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  }

  return (
    <Dialog
      title="Mật khẩu tạm"
      onClose={onClose}
      footer={
        <Button variant="secondary" onClick={onClose}>
          Tôi đã đưa mật khẩu cho người dùng
        </Button>
      }
    >
      <p>
        Mật khẩu tạm của <strong>{result.fullName}</strong> (<span className="font-mono">{result.username}</span>):
      </p>
      <div className="flex items-center justify-between gap-3 rounded-lg bg-slate-100 px-4 py-3">
        <span className="select-all font-mono text-2xl font-bold tracking-widest">{result.temporaryPassword}</span>
        <Button size="sm" variant="secondary" onClick={() => void copy()}>
          {copied ? 'Đã chép' : 'Chép'}
        </Button>
      </div>
      <Alert tone="warning">
        Mật khẩu chỉ hiện một lần. Lần đăng nhập tới, người dùng bắt buộc đổi sang mật khẩu của riêng họ; các phiên đang
        mở của tài khoản này đã bị đăng xuất.
      </Alert>
    </Dialog>
  );
}
