# deploy

服务器侧配置与部署资料。**这里只放模板/示例，真实密钥一律留在服务器上。**

| 文件 | 作用 |
|---|---|
| `nginx/hiddenwhite.conf` | 公网单入口反向代理：静态站点 + `/api` `/uploads` 反代 + 限流 + 默认拒绝 |
| `nginx/hw-gateway.conf.example` | 网关密钥文件示例（复制到 `/etc/nginx/hw-gateway.conf`，`chmod 600`） |

## 拓扑

```
客户端 → https://coffee.example.com (nginx) → 127.0.0.1:8080 (Spring Boot)
```

后端只监听回环，且只接受带 `X-HW-Gateway` 头的请求；客户端拿不到后端地址，也连不上端口。

## 三步上线

```bash
# 1) 密钥
openssl rand -hex 32
#    后端：HW_GATEWAY_SECRET=<值>
#    nginx：/etc/nginx/hw-gateway.conf 写入 set $hw_gateway_secret "<值>";

# 2) 配置
sudo install -m 600 -o root -g root hw-gateway.conf /etc/nginx/hw-gateway.conf
sudo cp nginx/hiddenwhite.conf /etc/nginx/conf.d/
sudo nginx -t && sudo systemctl reload nginx

# 3) 后端（systemd Environment= 或 shell）
SPRING_PROFILES_ACTIVE=prod SERVER_ADDRESS=127.0.0.1 HW_GATEWAY_SECRET=… java -jar hiddenwhite-server.jar
```

自检：

```bash
curl -I https://coffee.example.com/api/health                  # 200（经 nginx 自动带密钥）
curl -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8080/api/health   # 403（缺密钥）
curl -m 3 http://<公网IP>:8080/api/health                      # 连不上（只监听回环）
```

## 站点根布局

一个 `root` 服务两个前端，避免 `alias` + `try_files` 的坑：

```
/var/www/hiddenwhite/          ← 点单端（HiddenWhite-front: dist/build/h5）
/var/www/hiddenwhite/admin/    ← 管理端（HiddenWhite-admin: dist）
```

完整说明、改动清单、兼容性注意：**`docs/后端暴露面收敛.md`**
