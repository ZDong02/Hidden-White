package com.hiddenwhite.admin.redeem.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 积分运营数据访问：直读点单端使用的 {@code hidden_white} 库。
 *
 * <p>与 {@code CoffeeMapper} 同一套路：{@code ${coffeeDb}.} 前缀 + 注解 SQL +
 * camelCase 别名返回 {@code Map}。库名由 {@code mybatis-plus.configuration-properties.coffeeDb}
 * 注入，可用环境变量 {@code COFFEE_DB_NAME} 覆盖。
 *
 * <p>兑换记录列表刻意 {@code LEFT JOIN members} 取昵称：运营看到的是「谁兑的」，
 * 而 {@code member_id} 是一串 UUID，单看它无法判断是否需要联系顾客。
 */
@Mapper
public interface RedeemMapper {

    // ---- 兑换品 ----

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM ${coffeeDb}.redeem_products
            WHERE (#{keyword} IS NULL OR #{keyword} = ''
                   OR id LIKE CONCAT('%', #{keyword}, '%')
                   OR name LIKE CONCAT('%', #{keyword}, '%')
                   OR subtitle LIKE CONCAT('%', #{keyword}, '%'))
              AND (#{enabled} IS NULL OR enabled = #{enabled})
            </script>
            """)
    long countProducts(@Param("keyword") String keyword, @Param("enabled") Boolean enabled);

    @Select("""
            <script>
            SELECT id, name, subtitle, description, image, points_cost AS pointsCost, stock,
                   per_member_limit AS perMemberLimit, sort_order AS sortOrder, enabled,
                   DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS createdAt
            FROM ${coffeeDb}.redeem_products
            WHERE (#{keyword} IS NULL OR #{keyword} = ''
                   OR id LIKE CONCAT('%', #{keyword}, '%')
                   OR name LIKE CONCAT('%', #{keyword}, '%')
                   OR subtitle LIKE CONCAT('%', #{keyword}, '%'))
              AND (#{enabled} IS NULL OR enabled = #{enabled})
            ORDER BY sort_order, id
            LIMIT #{offset}, #{size}
            </script>
            """)
    List<Map<String, Object>> pageProducts(@Param("keyword") String keyword,
                                           @Param("enabled") Boolean enabled,
                                           @Param("offset") long offset,
                                           @Param("size") int size);

    @Select("SELECT COUNT(*) FROM ${coffeeDb}.redeem_products WHERE id = #{id}")
    long countProductId(@Param("id") String id);

    @Insert("""
            INSERT INTO ${coffeeDb}.redeem_products
                (id, name, subtitle, description, image, points_cost, stock, per_member_limit, sort_order, enabled)
            VALUES (#{id}, #{name}, #{subtitle}, #{description}, #{image}, #{pointsCost},
                    #{stock}, #{perMemberLimit}, #{sortOrder}, #{enabled})
            """)
    int insertProduct(@Param("id") String id,
                      @Param("name") String name,
                      @Param("subtitle") String subtitle,
                      @Param("description") String description,
                      @Param("image") String image,
                      @Param("pointsCost") int pointsCost,
                      @Param("stock") Integer stock,
                      @Param("perMemberLimit") int perMemberLimit,
                      @Param("sortOrder") int sortOrder,
                      @Param("enabled") boolean enabled);

    @Update("""
            UPDATE ${coffeeDb}.redeem_products
            SET name = #{name}, subtitle = #{subtitle}, description = #{description}, image = #{image},
                points_cost = #{pointsCost}, stock = #{stock}, per_member_limit = #{perMemberLimit},
                sort_order = #{sortOrder}, enabled = #{enabled}
            WHERE id = #{id}
            """)
    int updateProduct(@Param("id") String id,
                      @Param("name") String name,
                      @Param("subtitle") String subtitle,
                      @Param("description") String description,
                      @Param("image") String image,
                      @Param("pointsCost") int pointsCost,
                      @Param("stock") Integer stock,
                      @Param("perMemberLimit") int perMemberLimit,
                      @Param("sortOrder") int sortOrder,
                      @Param("enabled") boolean enabled);

    /** 只改状态位：整行更新要求名称 / 积分价齐全，一键上下架只带 enabled。 */
    @Update("UPDATE ${coffeeDb}.redeem_products SET enabled = #{enabled} WHERE id = #{id}")
    int updateProductEnabled(@Param("id") String id, @Param("enabled") boolean enabled);

    @Delete("DELETE FROM ${coffeeDb}.redeem_products WHERE id = #{id}")
    int deleteProduct(@Param("id") String id);

    /** 已被兑换单引用的兑换品不能物理删除，否则历史凭证失去指向。 */
    @Select("SELECT COUNT(*) FROM ${coffeeDb}.redemptions WHERE product_id = #{id}")
    long countRedemptionsByProduct(@Param("id") String id);

    // ---- 兑换记录 ----

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM ${coffeeDb}.redemptions r
            LEFT JOIN ${coffeeDb}.members m ON m.id = r.member_id
            WHERE (#{keyword} IS NULL OR #{keyword} = ''
                   OR r.redeem_no LIKE CONCAT('%', #{keyword}, '%')
                   OR r.code LIKE CONCAT('%', #{keyword}, '%')
                   OR r.product_name LIKE CONCAT('%', #{keyword}, '%')
                   OR r.member_id LIKE CONCAT('%', #{keyword}, '%')
                   OR m.display_name LIKE CONCAT('%', #{keyword}, '%'))
              AND (#{status} IS NULL OR r.status = #{status})
            </script>
            """)
    long countRedemptions(@Param("keyword") String keyword, @Param("status") String status);

    @Select("""
            <script>
            SELECT r.id, r.redeem_no AS redeemNo, r.member_id AS memberId, m.display_name AS memberName,
                   r.product_id AS productId, r.product_name AS productName, r.product_image AS productImage,
                   r.points_cost AS pointsCost, r.code, r.status,
                   DATE_FORMAT(r.expires_at, '%Y-%m-%d %H:%i:%s') AS expiresAt,
                   DATE_FORMAT(r.verified_at, '%Y-%m-%d %H:%i:%s') AS verifiedAt,
                   r.verified_by AS verifiedBy,
                   DATE_FORMAT(r.cancelled_at, '%Y-%m-%d %H:%i:%s') AS cancelledAt,
                   DATE_FORMAT(r.created_at, '%Y-%m-%d %H:%i:%s') AS createdAt
            FROM ${coffeeDb}.redemptions r
            LEFT JOIN ${coffeeDb}.members m ON m.id = r.member_id
            WHERE (#{keyword} IS NULL OR #{keyword} = ''
                   OR r.redeem_no LIKE CONCAT('%', #{keyword}, '%')
                   OR r.code LIKE CONCAT('%', #{keyword}, '%')
                   OR r.product_name LIKE CONCAT('%', #{keyword}, '%')
                   OR r.member_id LIKE CONCAT('%', #{keyword}, '%')
                   OR m.display_name LIKE CONCAT('%', #{keyword}, '%'))
              AND (#{status} IS NULL OR r.status = #{status})
            ORDER BY r.created_at DESC, r.id
            LIMIT #{offset}, #{size}
            </script>
            """)
    List<Map<String, Object>> pageRedemptions(@Param("keyword") String keyword,
                                              @Param("status") String status,
                                              @Param("offset") long offset,
                                              @Param("size") int size);

    /** 按核销码查单，供核销失败时给出准确原因（不存在 / 已核销 / 已过期）。 */
    @Select("""
            SELECT id, redeem_no AS redeemNo, product_name AS productName, points_cost AS pointsCost,
                   status, DATE_FORMAT(expires_at, '%Y-%m-%d %H:%i') AS expiresAt,
                   DATE_FORMAT(verified_at, '%Y-%m-%d %H:%i') AS verifiedAt,
                   (expires_at <= NOW(6)) AS expired
            FROM ${coffeeDb}.redemptions
            WHERE code = #{code}
            """)
    Map<String, Object> findByCode(@Param("code") String code);

    /**
     * 核销：只允许「待核销且未过期」翻转。
     *
     * <p>过期判定与状态判定都写进 WHERE：单独先查再改会给并发留窗口
     * （店员连点两下、两个店员同时输同一个码），条件更新天然幂等。
     */
    @Update("""
            UPDATE ${coffeeDb}.redemptions
            SET status = 'REDEEMED', verified_at = NOW(6), verified_by = #{operator}
            WHERE code = #{code} AND status = 'PENDING' AND expires_at > NOW(6)
            """)
    int verifyByCode(@Param("code") String code, @Param("operator") String operator);
}
