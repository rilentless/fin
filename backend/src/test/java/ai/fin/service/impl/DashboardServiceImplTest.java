package ai.fin.service.impl;

import ai.fin.dto.dashboard.*;
import ai.fin.enums.TxnType;
import ai.fin.repository.TransactionRepository;
import ai.fin.cache.DashboardCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardServiceImplTest {

  @Mock
  private TransactionRepository transactionRepository;

  @Mock
  private DashboardCacheService dashboardCacheService;

  @InjectMocks
  private DashboardServiceImpl dashboardService;

  private final String userId = "user-123";
  private final YearMonth month = YearMonth.of(2026, 3);
  private final LocalDate from = LocalDate.of(2026, 3, 1);
  private final LocalDate to = LocalDate.of(2026, 3, 31);

  @BeforeEach
  void setUp() {
    // By default, make dashboardCacheService invoke the supplier
    when(dashboardCacheService.getOrCompute(eq(userId), eq(month), any())).thenAnswer(invocation -> {
      Supplier<DashboardResponse> supplier = invocation.getArgument(2);
      return supplier.get();
    });
  }

  @Test
  @DisplayName("getDashboard aggregates financial data correctly from repository")
  void shouldComputeDashboardSuccessfully() {
    when(transactionRepository.sumByUserAndTypeAndDateBetween(userId, TxnType.INCOME, from, to))
        .thenReturn(new BigDecimal("10000.00"));
    when(transactionRepository.sumByUserAndTypeAndDateBetween(userId, TxnType.EXPENSE, from, to))
        .thenReturn(new BigDecimal("4000.00"));

    CategoryBreakdownItem expenseBreakdown = new CategoryBreakdownItem("Groceries", new BigDecimal("2000.00"));
    when(transactionRepository.findCategoryBreakdown(userId, TxnType.EXPENSE, from, to))
        .thenReturn(List.of(expenseBreakdown));

    CategoryBreakdownItem incomeBreakdown = new CategoryBreakdownItem("Salary", new BigDecimal("10000.00"));
    when(transactionRepository.findCategoryBreakdown(userId, TxnType.INCOME, from, to))
        .thenReturn(List.of(incomeBreakdown));

    DailyTrendItem dailyTrend = new DailyTrendItem(LocalDate.of(2026, 3, 5), new BigDecimal("10000.00"), new BigDecimal("500.00"));
    when(transactionRepository.findDailyTrend(userId, from, to))
        .thenReturn(List.of(dailyTrend));

    TopExpenseItem topExpense = new TopExpenseItem("tx-1", new BigDecimal("1500.00"), "Groceries", "Supermarket", LocalDate.of(2026, 3, 5));
    when(transactionRepository.findTop5Expenses(userId, from, to))
        .thenReturn(List.of(topExpense));

    DashboardResponse response = dashboardService.getDashboard(userId, month);

    assertThat(response).isNotNull();
    assertThat(response.month()).isEqualTo("2026-03");
    assertThat(response.summary().totalIncome()).isEqualByComparingTo("10000.00");
    assertThat(response.summary().totalExpense()).isEqualByComparingTo("4000.00");
    assertThat(response.summary().netBalance()).isEqualByComparingTo("6000.00");
    assertThat(response.expenseByCategory()).hasSize(1);
    assertThat(response.expenseByCategory().get(0).categoryName()).isEqualTo("Groceries");
    assertThat(response.expenseByCategory().get(0).percentage()).isEqualTo(50.0);
    assertThat(response.incomeByCategory()).hasSize(1);
    assertThat(response.incomeByCategory().get(0).percentage()).isEqualTo(100.0);
    assertThat(response.dailyTrend()).hasSize(1);
    assertThat(response.topExpenses()).hasSize(1);

    verify(dashboardCacheService).getOrCompute(eq(userId), eq(month), any());
  }

  @Test
  @DisplayName("getDashboard handles zero transaction / empty state gracefully")
  void shouldHandleEmptyDashboard() {
    when(transactionRepository.sumByUserAndTypeAndDateBetween(userId, TxnType.INCOME, from, to))
        .thenReturn(BigDecimal.ZERO);
    when(transactionRepository.sumByUserAndTypeAndDateBetween(userId, TxnType.EXPENSE, from, to))
        .thenReturn(BigDecimal.ZERO);
    when(transactionRepository.findCategoryBreakdown(userId, TxnType.EXPENSE, from, to))
        .thenReturn(Collections.emptyList());
    when(transactionRepository.findCategoryBreakdown(userId, TxnType.INCOME, from, to))
        .thenReturn(Collections.emptyList());
    when(transactionRepository.findDailyTrend(userId, from, to))
        .thenReturn(Collections.emptyList());
    when(transactionRepository.findTop5Expenses(userId, from, to))
        .thenReturn(Collections.emptyList());

    DashboardResponse response = dashboardService.getDashboard(userId, month);

    assertThat(response).isNotNull();
    assertThat(response.month()).isEqualTo("2026-03");
    assertThat(response.summary().totalIncome()).isEqualByComparingTo("0");
    assertThat(response.summary().totalExpense()).isEqualByComparingTo("0");
    assertThat(response.summary().netBalance()).isEqualByComparingTo("0");
    assertThat(response.expenseByCategory()).isEmpty();
    assertThat(response.incomeByCategory()).isEmpty();
    assertThat(response.dailyTrend()).isEmpty();
    assertThat(response.topExpenses()).isEmpty();
  }
}
