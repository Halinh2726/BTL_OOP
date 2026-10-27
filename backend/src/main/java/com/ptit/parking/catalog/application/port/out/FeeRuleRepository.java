package com.ptit.parking.catalog.application.port.out;

import com.ptit.parking.catalog.domain.FeeRule;
import java.util.List;

/** Port: đọc bảng giá. Sau này chính sách tính phí (FeePolicy) dùng lại port này. */
public interface FeeRuleRepository {

    List<FeeRule> allFeeRules();
}