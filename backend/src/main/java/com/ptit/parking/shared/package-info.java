/**
 * Shared kernel: chỉ chứa những thứ THẬT SỰ dùng chung cho mọi module.
 * <ul>
 *   <li>{@code shared.domain}  – DomainException, ErrorCode (lỗi nghiệp vụ dùng chung)</li>
 *   <li>{@code shared.presentation.web} – định dạng lỗi thống nhất, health check</li>
 *   <li>{@code shared.infrastructure.security} – phiên, CSRF, phân quyền</li>
 *   <li>{@code shared.infrastructure.ratelimit} – giới hạn tần suất gọi API</li>
 *   <li>{@code shared.configuration} – Clock, múi giờ</li>
 * </ul>
 * Không phải "sọt rác": logic nghiệp vụ của một module cụ thể KHÔNG được đặt ở đây.
 */
package com.ptit.parking.shared;
