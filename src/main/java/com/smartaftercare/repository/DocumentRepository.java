package com.smartaftercare.repository;

import com.smartaftercare.model.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * 文档 JPA 数据访问层
 */
@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    /**
     * 按品牌和型号分页查询
     */
    @Query("SELECT d FROM Document d WHERE " +
            "(:brand IS NULL OR :brand = '' OR d.brand = :brand) AND " +
            "(:model IS NULL OR :model = '' OR d.model = :model) " +
            "ORDER BY d.createdAt DESC")
    Page<Document> findByBrandAndModel(
            @Param("brand") String brand,
            @Param("model") String model,
            Pageable pageable);

    /**
     * 更新文档状态
     */
    @Modifying
    @Query("UPDATE Document d SET d.status = :status WHERE d.id = :id")
    void updateStatus(@Param("id") Long id, @Param("status") String status);

    /**
     * 统计已处理文档数
     */
    long countByStatus(String status);
}
