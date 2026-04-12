package com.smartaftercare.repository;

import com.smartaftercare.model.QueryLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * 查询日志 JPA 数据访问层
 */
@Repository
public interface QueryLogRepository extends JpaRepository<QueryLog, Long> {

    /**
     * 统计今日查询数
     */
    @Query("SELECT COUNT(q) FROM QueryLog q WHERE FUNCTION('DATE', q.createdAt) = FUNCTION('CURRENT_DATE')")
    long countTodayQueries();
}
