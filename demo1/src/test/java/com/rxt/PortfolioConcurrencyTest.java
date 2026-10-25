package com.rxt;

import com.rxt.common.exception.BizException;
import com.rxt.dto.AuditDTO;
import com.rxt.dto.LoginDTO;
import com.rxt.entity.Article;
import com.rxt.entity.User;
import com.rxt.mapper.ArticleMapper;
import com.rxt.mapper.UserMapper;
import com.rxt.service.AdminService;
import com.rxt.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PortfolioConcurrencyTest {
    @Autowired UserMapper users;
    @Autowired ArticleMapper articles;
    @Autowired UserService userService;
    @Autowired AdminService adminService;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;

    @Test
    void concurrentWrongPasswordsDoNotLoseFailureCounts() throws Exception {
        User user = new User();
        user.setPhone("19900000991"); user.setPassword(encoder.encode("right-password"));
        user.setName("并发测试"); user.setRole("farmer"); user.setStatus(1);
        user.setFailCount(0); user.setTokenVersion(0); users.insert(user);
        try {
            parallel(5, () -> {
                LoginDTO dto = new LoginDTO(); dto.setPhone(user.getPhone()); dto.setPassword("wrong-password");
                assertThrows(BizException.class, () -> userService.login(dto));
            });
            User result = users.selectById(user.getId());
            assertNotNull(result.getLockUntil());
            assertEquals(0, result.getFailCount());
        } finally {
            jdbc.update("DELETE FROM t_user WHERE id = ?", user.getId());
        }
    }

    @Test
    void competingReviewersCanCommitOnlyOneDecision() throws Exception {
        Article article = new Article(); article.setTitle("并发审核测试"); article.setContent("测试正文");
        article.setAuthorId(5L); article.setAuthorRole("farmer"); article.setStatus(0);
        article.setViewCount(0); article.setLikeCount(0); article.setCommentCount(0); articles.insert(article);
        AtomicInteger successes = new AtomicInteger();
        try {
            parallel(2, () -> {
                AuditDTO dto = new AuditDTO(); dto.setStatus("APPROVED");
                try { adminService.auditArticle(1L, article.getId(), dto); successes.incrementAndGet(); }
                catch (BizException expected) { assertTrue(expected.getMessage().contains("审核")); }
            });
            assertEquals(1, successes.get());
            assertEquals(1, articles.selectById(article.getId()).getStatus());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM t_operation_log WHERE params = ? AND operation = '通过文章审核'",
                    Integer.class, "targetId=" + article.getId()));
        } finally {
            jdbc.update("DELETE FROM t_operation_log WHERE params = ?", "targetId=" + article.getId());
            jdbc.update("DELETE FROM t_article WHERE id = ?", article.getId());
        }
    }

    private void parallel(int count, Runnable action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var tasks = new ArrayList<Future<?>>();
            for (int i = 0; i < count; i++) tasks.add(pool.submit(() -> { start.await(); action.run(); return null; }));
            start.countDown();
            for (Future<?> task : tasks) task.get(30, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
    }
}
