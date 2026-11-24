package ai.fin.service.impl;

import ai.fin.dto.transaction.CreateTransactionRequest;
import ai.fin.dto.transaction.TransactionResponse;
import ai.fin.dto.transaction.UpdateTransactionRequest;
import ai.fin.entities.Category;
import ai.fin.entities.PaymentMode;
import ai.fin.entities.Transaction;
import ai.fin.entities.User;
import ai.fin.enums.CategoryType;
import ai.fin.enums.TxnType;
import ai.fin.rag.SemanticCacheService;
import ai.fin.rag.service.EmbeddingService;
import ai.fin.repository.CategoryRepository;
import ai.fin.repository.PaymentModeRepository;
import ai.fin.repository.TransactionRepository;
import ai.fin.repository.UserRepository;
import ai.fin.service.BudgetAlertService;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceImplTest {

  @Mock
  private EmbeddingService embeddingService;

  @Mock
  private BudgetAlertService budgetAlertService;

  @Mock
  private SemanticCacheService semanticCacheService;

  @Mock
  private DashboardCacheService dashboardCacheService;

  @Mock
  private UserRepository userRepository;

  @Mock
  private CategoryRepository categoryRepository;

  @Mock
  private PaymentModeRepository paymentModeRepository;

  @Mock
  private TransactionRepository transactionRepository;

  @InjectMocks
  private TransactionServiceImpl transactionService;

  private User testUser;
  private Category testCategory;
  private PaymentMode testPaymentMode;

  @BeforeEach
  void setUp() {
    testUser = new User();
    testUser.setId("user-123");
    testUser.setEmail("user@fin.ai");

    testCategory = new Category();
    testCategory.setId("cat-1");
    testCategory.setName("Food");
    testCategory.setCategoryType(CategoryType.EXPENSE);

    testPaymentMode = new PaymentMode();
    testPaymentMode.setId("pm-1");
    testPaymentMode.setName("Credit Card");
  }

  @Test
  @DisplayName("create should save transaction and evict dashboard cache for the transaction month")
  void shouldCreateTransactionAndEvictCache() {
    LocalDate txnDate = LocalDate.of(2026, 3, 15);
    CreateTransactionRequest request = new CreateTransactionRequest(
        new BigDecimal("150.00"),
        TxnType.EXPENSE,
        txnDate,
        "Lunch with team",
        "cat-1",
        "pm-1"
    );

    when(userRepository.getReferenceById("user-123")).thenReturn(testUser);
    when(categoryRepository.findAllVisibleToUser("user-123", CategoryType.EXPENSE))
        .thenReturn(List.of(testCategory));
    when(paymentModeRepository.findAllVisibleToUser("user-123"))
        .thenReturn(List.of(testPaymentMode));

    TransactionResponse response = transactionService.create("user-123", request);

    assertThat(response).isNotNull();
    assertThat(response.amount()).isEqualByComparingTo("150.00");
    assertThat(response.type()).isEqualTo(TxnType.EXPENSE);

    verify(transactionRepository).saveAndFlush(any(Transaction.class));
    verify(dashboardCacheService).evictUserMonth("user-123", YearMonth.of(2026, 3));
    verify(semanticCacheService).evictUserCache("user-123");
  }

  @Test
  @DisplayName("update should update transaction and evict dashboard cache for both old and new months")
  void shouldUpdateTransactionAndEvictBothMonthsIfDateChanged() {
    Transaction existing = new Transaction();
    existing.setId("tx-1");
    existing.setUser(testUser);
    existing.setAmount(new BigDecimal("100.00"));
    existing.setType(TxnType.EXPENSE);
    existing.setDate(LocalDate.of(2026, 2, 28));
    existing.setCategory(testCategory);
    existing.setPaymentMode(testPaymentMode);

    when(transactionRepository.findByIdAndUser_Id("tx-1", "user-123"))
        .thenReturn(Optional.of(existing));

    UpdateTransactionRequest updateRequest = new UpdateTransactionRequest(
        new BigDecimal("200.00"),
        TxnType.EXPENSE,
        LocalDate.of(2026, 3, 5),
        "Updated grocery note",
        null,
        null
    );

    TransactionResponse response = transactionService.update("user-123", "tx-1", updateRequest);

    assertThat(response).isNotNull();
    assertThat(response.amount()).isEqualByComparingTo("200.00");

    verify(transactionRepository).flush();
    // Old month: 2026-02, New month: 2026-03
    verify(dashboardCacheService).evictUserMonth("user-123", YearMonth.of(2026, 2));
    verify(dashboardCacheService).evictUserMonth("user-123", YearMonth.of(2026, 3));
    verify(semanticCacheService).evictUserCache("user-123");
  }

  @Test
  @DisplayName("delete should delete transaction and evict dashboard cache for the transaction month")
  void shouldDeleteTransactionAndEvictCache() {
    Transaction existing = new Transaction();
    existing.setId("tx-1");
    existing.setUser(testUser);
    existing.setAmount(new BigDecimal("100.00"));
    existing.setType(TxnType.EXPENSE);
    existing.setDate(LocalDate.of(2026, 3, 10));
    existing.setEmbeddingId("embed-1");

    when(transactionRepository.findByIdAndUser_Id("tx-1", "user-123"))
        .thenReturn(Optional.of(existing));

    transactionService.delete("user-123", "tx-1");

    verify(embeddingService).delete("embed-1");
    verify(transactionRepository).delete(existing);
    verify(dashboardCacheService).evictUserMonth("user-123", YearMonth.of(2026, 3));
    verify(semanticCacheService).evictUserCache("user-123");
  }
}
