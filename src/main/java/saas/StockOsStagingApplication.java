package saas;

import app.ProductionSafetyGate;
import app.StagingSupabaseSettings;
import app.StockOsSecurityConfiguration;
import controller.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.context.annotation.Import;
import service.*;

/**
 * Standalone StockOS SaaS entrypoint. Never scans legacy controllers, repositories,
 * JPA entities, demo-seed beans or the legacy app.Main.
 *
 * Run with: --spring.profiles.active=staging and
 * STOCKOS_SECURITY_MODE=supabase-jwt. Flyway is deliberately disabled in
 * staging because Supabase managed the private migrations independently.
 */
@SpringBootApplication(
        scanBasePackages = "saas",
        exclude = {HibernateJpaAutoConfiguration.class, JpaRepositoriesAutoConfiguration.class})
@Import({
        ProductionSafetyGate.class,
        StagingSupabaseSettings.class,
        StockOsSecurityConfiguration.class,
        GlobalExceptionHandler.class,
        SaasAdminController.class,
        SaasCashController.class,
        SaasComandaController.class,
        SaasEmployeeController.class,
        SaasEmployeeInvitationController.class,
        SaasGoodsReceiptController.class,
        SaasInventoryAdjustmentController.class,
        SaasPostCloseRefundController.class,
        SaasProdutoController.class,
        SaasPublicConfigController.class,
        SaasReportsController.class,
        SaasStockReturnController.class,
        SaasTenantController.class,
        SaasAdminAccess.class,
        SaasAdminService.class,
        SaasCashService.class,
        SaasComandaService.class,
        SaasEmployeeInvitations.class,
        SaasEmployeeService.class,
        SaasGoodsReceiptService.class,
        SaasInventoryAdjustmentService.class,
        SaasLicenseService.class,
        SaasPostCloseRefundService.class,
        SaasProdutoService.class,
        SaasReportsService.class,
        SaasStockReturnService.class,
        SaasTenantAccess.class,
        SaasVerifiedAuthIdentity.class
})
public class StockOsStagingApplication {
    public static void main(String[] args) {
        SpringApplication.run(StockOsStagingApplication.class,args);
    }
}
