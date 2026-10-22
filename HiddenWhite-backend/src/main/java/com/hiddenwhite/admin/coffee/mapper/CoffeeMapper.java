package com.hiddenwhite.admin.coffee.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 咖啡业务数据访问：直接读写点单端使用的 hidden_white 库，
 * 与小程序 / H5 后端（HiddenWhite-backend）通过同一个数据库实现三端数据互通。
 * 库名由 application-dev.yml 的 mybatis-plus.configuration-properties.coffeeDb 注入，
 * 默认 hidden_white，可用环境变量 COFFEE_DB_NAME 覆盖。
 */
@Mapper
public interface CoffeeMapper {

    // ---- 商品 ----

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM ${coffeeDb}.products
            WHERE (#{keyword} IS NULL OR #{keyword} = ''
                   OR id LIKE CONCAT('%', #{keyword}, '%')
                   OR name LIKE CONCAT('%', #{keyword}, '%')
                   OR subtitle LIKE CONCAT('%', #{keyword}, '%'))
              AND (#{category} IS NULL OR category = #{category})
              AND (#{enabled} IS NULL OR enabled = #{enabled})
            </script>
            """)
    long countProducts(@Param("keyword") String keyword,
                       @Param("category") Integer category,
                       @Param("enabled") Boolean enabled);

    @Select("""
            <script>
            SELECT id, name, subtitle, description, detail_description AS detailDescription,
                   price, image, detail_image AS detailImage, category, has_milk AS hasMilk, enabled
            FROM ${coffeeDb}.products
            WHERE (#{keyword} IS NULL OR #{keyword} = ''
                   OR id LIKE CONCAT('%', #{keyword}, '%')
                   OR name LIKE CONCAT('%', #{keyword}, '%')
                   OR subtitle LIKE CONCAT('%', #{keyword}, '%'))
              AND (#{category} IS NULL OR category = #{category})
              AND (#{enabled} IS NULL OR enabled = #{enabled})
            ORDER BY category, id
            LIMIT #{offset}, #{size}
            </script>
            """)
    List<Map<String, Object>> pageProducts(@Param("keyword") String keyword,
                                           @Param("category") Integer category,
                                           @Param("enabled") Boolean enabled,
                                           @Param("offset") long offset,
                                           @Param("size") int size);

    @Select("SELECT COUNT(*) FROM ${coffeeDb}.products WHERE id = #{id}")
    long countProductId(@Param("id") String id);

    @Insert("""
            INSERT INTO ${coffeeDb}.products
                (id, name, subtitle, description, detail_description, price, image, detail_image, category, has_milk, enabled)
            VALUES (#{id}, #{name}, #{subtitle}, #{description}, #{detailDescription},
                    #{price}, #{image}, #{detailImage}, #{category}, #{hasMilk}, #{enabled})
            """)
    int insertProduct(@Param("id") String id,
                      @Param("name") String name,
                      @Param("subtitle") String subtitle,
                      @Param("description") String description,
                      @Param("detailDescription") String detailDescription,
                      @Param("price") BigDecimal price,
                      @Param("image") String image,
                      @Param("detailImage") String detailImage,
                      @Param("category") int category,
                      @Param("hasMilk") boolean hasMilk,
                      @Param("enabled") boolean enabled);

    @Update("""
            UPDATE ${coffeeDb}.products
            SET name = #{name}, subtitle = #{subtitle}, description = #{description},
                detail_description = #{detailDescription}, price = #{price}, image = #{image},
                detail_image = #{detailImage}, category = #{category}, has_milk = #{hasMilk}, enabled = #{enabled}
            WHERE id = #{id}
            """)
    int updateProduct(@Param("id") String id,
                      @Param("name") String name,
                      @Param("subtitle") String subtitle,
                      @Param("description") String description,
                      @Param("detailDescription") String detailDescription,
                      @Param("price") BigDecimal price,
                      @Param("image") String image,
                      @Param("detailImage") String detailImage,
                      @Param("category") int category,
                      @Param("hasMilk") boolean hasMilk,
                      @Param("enabled") boolean enabled);

    @Delete("DELETE FROM ${coffeeDb}.products WHERE id = #{id}")
    int deleteProduct(@Param("id") String id);

    /** 商品被订单引用时无法物理删除，改为下架（软删除）。 */
    @Update("UPDATE ${coffeeDb}.products SET enabled = 0 WHERE id = #{id}")
    int disableProduct(@Param("id") String id);

    @Select("SELECT COUNT(*) FROM ${coffeeDb}.order_items WHERE product_id = #{id}")
    long countProductOrders(@Param("id") String id);

    // ---- 规格选项 ----

    @Select("""
            SELECT id, option_type AS type, label, extra_price AS extraPrice,
                   sort_order AS sortOrder, enabled
            FROM ${coffeeDb}.product_options
            ORDER BY option_type, sort_order, id
            """)
    List<Map<String, Object>> listOptions();

    @Select("SELECT COUNT(*) FROM ${coffeeDb}.product_options WHERE option_type = #{type} AND label = #{label}")
    long countOption(@Param("type") String type, @Param("label") String label);

    @Insert("""
            INSERT INTO ${coffeeDb}.product_options (option_type, label, extra_price, sort_order, enabled)
            VALUES (#{type}, #{label}, #{extraPrice}, #{sortOrder}, #{enabled})
            """)
    int insertOption(@Param("type") String type,
                     @Param("label") String label,
                     @Param("extraPrice") BigDecimal extraPrice,
                     @Param("sortOrder") int sortOrder,
                     @Param("enabled") boolean enabled);

    @Select("SELECT id FROM ${coffeeDb}.product_options WHERE option_type = #{type} AND label = #{label} ORDER BY id LIMIT 1")
    Long findOptionId(@Param("type") String type, @Param("label") String label);

    @Update("""
            UPDATE ${coffeeDb}.product_options
            SET option_type = #{type}, label = #{label}, extra_price = #{extraPrice},
                sort_order = #{sortOrder}, enabled = #{enabled}
            WHERE id = #{id}
            """)
    int updateOption(@Param("id") long id,
                     @Param("type") String type,
                     @Param("label") String label,
                     @Param("extraPrice") BigDecimal extraPrice,
                     @Param("sortOrder") int sortOrder,
                     @Param("enabled") boolean enabled);

    @Delete("DELETE FROM ${coffeeDb}.product_options WHERE id = #{id}")
    int deleteOption(@Param("id") long id);

    // ---- 订单 ----

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM ${coffeeDb}.orders
            WHERE (#{keyword} IS NULL OR #{keyword} = ''
                   OR order_number LIKE CONCAT('%', #{keyword}, '%')
                   OR pickup LIKE CONCAT('%', #{keyword}, '%')
                   OR member_id LIKE CONCAT('%', #{keyword}, '%'))
              AND (#{status} IS NULL OR status = #{status})
            </script>
            """)
    long countOrders(@Param("keyword") String keyword, @Param("status") String status);

    @Select("""
            <script>
            SELECT id, member_id AS memberId, subtotal, discount, packaging_fee AS packagingFee,
                   total, item_count AS itemCount, pickup, order_number AS orderNumber,
                   DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS createdAt,
                   DATE_FORMAT(ready_at, '%Y-%m-%d %H:%i:%s') AS readyAt,
                   status, note,
                   DATE_FORMAT(completed_at, '%Y-%m-%d %H:%i:%s') AS completedAt
            FROM ${coffeeDb}.orders
            WHERE (#{keyword} IS NULL OR #{keyword} = ''
                   OR order_number LIKE CONCAT('%', #{keyword}, '%')
                   OR pickup LIKE CONCAT('%', #{keyword}, '%')
                   OR member_id LIKE CONCAT('%', #{keyword}, '%'))
              AND (#{status} IS NULL OR status = #{status})
            ORDER BY created_at DESC
            LIMIT #{offset}, #{size}
            </script>
            """)
    List<Map<String, Object>> pageOrders(@Param("keyword") String keyword,
                                         @Param("status") String status,
                                         @Param("offset") long offset,
                                         @Param("size") int size);

    /**
     * 批量取多张订单的明细。
     *
     * <p>订单列表页原先是对每行单独查一次明细（{@code pageSize} 最大 100，
     * 即一次翻页最多 100 次往返）。这里改为一次 {@code IN} 查询，
     * 与 {@code ShopRepository.attachItems} 用的是同一套思路。
     */
    @Select("""
            <script>
            SELECT order_id AS orderId, product_name AS name, size_label AS size, temperature, milk,
                   quantity, unit_price AS unitPrice, line_total AS lineTotal
            FROM ${coffeeDb}.order_items
            WHERE order_id IN
            <foreach collection="orderIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            ORDER BY id
            </script>
            """)
    List<Map<String, Object>> orderItemsByOrderIds(@Param("orderIds") List<String> orderIds);

    /**
     * 完成取餐。
     *
     * <p>仅允许从 PAID 推进，与点单端 OrderService 保持同一条不变式：
     * 「没付钱不能标记取餐」。条件更新天然防并发。
     */
    @Update("""
            UPDATE ${coffeeDb}.orders
            SET status = 'COMPLETED', completed_at = NOW()
            WHERE id = #{id} AND status = 'PAID'
            """)
    int completeOrder(@Param("id") String id);

    /** 读取订单当前状态，供条件更新失败时给出准确提示。 */
    @Select("SELECT status FROM ${coffeeDb}.orders WHERE id = #{id}")
    String orderStatus(@Param("id") String id);

    @Delete("DELETE FROM ${coffeeDb}.orders WHERE id = #{id}")
    int deleteOrder(@Param("id") String id);

    @Select("SELECT COUNT(*) FROM ${coffeeDb}.orders")
    long countAllOrders();

    /** 未完成订单数：待支付 + 已支付制作中，两者都还未取餐。 */
    @Select("SELECT COUNT(*) FROM ${coffeeDb}.orders WHERE status IN ('CREATED','PENDING_PAYMENT','PAID')")
    long countInProgressOrders();

    @Select("SELECT COUNT(*) FROM ${coffeeDb}.members")
    long countMembers();

    // ---- 会员 ----

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM ${coffeeDb}.members
            WHERE (#{keyword} IS NULL OR #{keyword} = ''
                   OR id LIKE CONCAT('%', #{keyword}, '%')
                   OR display_name LIKE CONCAT('%', #{keyword}, '%')
                   OR phone LIKE CONCAT('%', #{keyword}, '%')
                   OR card_number LIKE CONCAT('%', #{keyword}, '%'))
            </script>
            """)
    long countMembersByKeyword(@Param("keyword") String keyword);

    @Select("""
            <script>
            SELECT id, display_name AS displayName, phone, member_level AS level,
                   card_number AS cardNumber, points, coupons, growth_value AS growthValue,
                   DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS createdAt
            FROM ${coffeeDb}.members
            WHERE (#{keyword} IS NULL OR #{keyword} = ''
                   OR id LIKE CONCAT('%', #{keyword}, '%')
                   OR display_name LIKE CONCAT('%', #{keyword}, '%')
                   OR phone LIKE CONCAT('%', #{keyword}, '%')
                   OR card_number LIKE CONCAT('%', #{keyword}, '%'))
            ORDER BY created_at
            LIMIT #{offset}, #{size}
            </script>
            """)
    List<Map<String, Object>> pageMembers(@Param("keyword") String keyword,
                                          @Param("offset") long offset,
                                          @Param("size") int size);

    @Update("""
            UPDATE ${coffeeDb}.members
            SET display_name = #{displayName}, member_level = #{level}, points = #{points},
                coupons = #{coupons}, growth_value = #{growthValue}
            WHERE id = #{id}
            """)
    int updateMember(@Param("id") String id,
                     @Param("displayName") String displayName,
                     @Param("level") String level,
                     @Param("points") int points,
                     @Param("coupons") int coupons,
                     @Param("growthValue") int growthValue);

    @Delete("DELETE FROM ${coffeeDb}.members WHERE id = #{id}")
    int deleteMember(@Param("id") String id);

    // ---- 权益 ----

    @Select("""
            SELECT id, icon, title, description, sort_order AS sortOrder, enabled
            FROM ${coffeeDb}.member_benefits
            ORDER BY sort_order, id
            """)
    List<Map<String, Object>> listBenefits();

    @Select("SELECT COUNT(*) FROM ${coffeeDb}.member_benefits WHERE title = #{title}")
    long countBenefitTitle(@Param("title") String title);

    @Insert("""
            INSERT INTO ${coffeeDb}.member_benefits (icon, title, description, sort_order, enabled)
            VALUES (#{icon}, #{title}, #{description}, #{sortOrder}, #{enabled})
            """)
    int insertBenefit(@Param("icon") String icon,
                      @Param("title") String title,
                      @Param("description") String description,
                      @Param("sortOrder") int sortOrder,
                      @Param("enabled") boolean enabled);

    @Select("SELECT id FROM ${coffeeDb}.member_benefits WHERE title = #{title} ORDER BY id LIMIT 1")
    Long findBenefitId(@Param("title") String title);

    @Update("""
            UPDATE ${coffeeDb}.member_benefits
            SET icon = #{icon}, title = #{title}, description = #{description},
                sort_order = #{sortOrder}, enabled = #{enabled}
            WHERE id = #{id}
            """)
    int updateBenefit(@Param("id") long id,
                      @Param("icon") String icon,
                      @Param("title") String title,
                      @Param("description") String description,
                      @Param("sortOrder") int sortOrder,
                      @Param("enabled") boolean enabled);

    @Delete("DELETE FROM ${coffeeDb}.member_benefits WHERE id = #{id}")
    int deleteBenefit(@Param("id") long id);

    // ---- 门店 ----

    @Select("""
            SELECT name, address, short_address AS shortAddress, latitude, longitude,
                   distance_label AS distance,
                   DATE_FORMAT(opening_time, '%H:%i') AS openingTime,
                   DATE_FORMAT(closing_time, '%H:%i') AS closingTime
            FROM ${coffeeDb}.stores ORDER BY id LIMIT 1
            """)
    Map<String, Object> store();

    @Update("""
            UPDATE ${coffeeDb}.stores
            SET name = #{name}, address = #{address}, short_address = #{shortAddress},
                latitude = #{latitude}, longitude = #{longitude}, distance_label = #{distance},
                opening_time = #{openingTime}, closing_time = #{closingTime}
            WHERE id = (SELECT id FROM (SELECT id FROM ${coffeeDb}.stores ORDER BY id LIMIT 1) t)
            """)
    int updateStore(@Param("name") String name,
                    @Param("address") String address,
                    @Param("shortAddress") String shortAddress,
                    @Param("latitude") BigDecimal latitude,
                    @Param("longitude") BigDecimal longitude,
                    @Param("distance") String distance,
                    @Param("openingTime") String openingTime,
                    @Param("closingTime") String closingTime);
}
