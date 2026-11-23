package com.ptit.parking.pass.application.usecase;

import com.ptit.parking.pass.application.port.out.PassCatalog;
import com.ptit.parking.pass.application.port.out.PassCatalog.VehiclePassPrice;
import java.util.List;

/** Quản lý bảng giá vé tháng theo loại xe (chỉ Quản lý). */
public class ManagePassCatalogUseCase {

    public record CatalogView(List<VehiclePassPrice> prices) {}

    private final PassCatalog catalog;

    public ManagePassCatalogUseCase(PassCatalog catalog) {
        this.catalog = catalog;
    }

    public CatalogView view() {
        return new CatalogView(catalog.allPrices());
    }

    public CatalogView updatePrice(String vehicleTypeCode, long monthlyPoints) {
        catalog.updatePrice(vehicleTypeCode, monthlyPoints);
        return view();
    }
}
