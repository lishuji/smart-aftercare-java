package com.smartaftercare.repository;

import com.smartaftercare.model.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 故障代码 JPA 数据访问层
 */
@Repository
public interface ErrorCodeRepository extends JpaRepository<ErrorCode, Long> {

    /**
     * 根据故障代码查询（不限型号）
     */
    Optional<ErrorCode> findFirstByCode(String code);

    /**
     * 根据故障代码和型号查询
     */
    Optional<ErrorCode> findFirstByCodeAndModel(String code, String model);

    /**
     * 按品牌和型号分页查询
     */
    @Query("SELECT e FROM ErrorCode e WHERE " +
            "(:brand IS NULL OR :brand = '' OR e.brand = :brand) AND " +
            "(:model IS NULL OR :model = '' OR e.model = :model) " +
            "ORDER BY e.code ASC")
    Page<ErrorCode> findByBrandAndModel(
            @Param("brand") String brand,
            @Param("model") String model,
            Pageable pageable);
}
