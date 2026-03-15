# 后端接口文档

基于后端控制器实现生成的前端接口说明。相关后端文件：

- `src/main/java/com/example/newai/controller/ChatController.java`
- `src/main/java/com/example/newai/controller/ChatHistoryController.java`
- `src/main/java/com/example/newai/controller/PdfController.java`
- VO: `src/main/java/com/example/newai/entity/VO/MessageVO.java`, `src/main/java/com/example/newai/entity/VO/Result.java`

> 注意：`/ai/chat` 与 `/ai/pdf/chat` 返回的是流式文本（Content-Type: text/html;charset=utf-8），前端需使用 `ReadableStream` 或 SSE/streaming 消费。

---

## 接口一：通用聊天（流式）

- 路径：`/ai/chat`
- 方法：GET 或 POST
- 参数：
  - `prompt` (string) — 用户输入的问题/提示
  - `chatId` (string) — 会话 id
- 请求示例（GET）：

```
GET /ai/chat?prompt=你好&chatId=chat123
```

- 响应：流式文本（每次返回一段字符串），Content-Type: `text/html;charset=utf-8`。
- 前端消费示例（Fetch + ReadableStream）：

```javascript
const res = await fetch('/ai/chat?prompt=你好&chatId=chat123');
const reader = res.body.getReader();
const decoder = new TextDecoder();
let text = '';
while(true){
  const {done, value} = await reader.read();
  if(done) break;
  const chunk = decoder.decode(value, {stream:true});
  // 处理 chunk（追加到 UI）
  text += chunk;
}
```

---

## 接口二：历史会话列表

- 路径：`/ai/history/{type}`
- 方法：GET
- 路径参数：
  - `type` (string) — 会话类型，例如 `chat` 或 `pdf`
- 响应：JSON Array[string]
- 响应示例：

```json
["chat123","chat456"]
```

---

## 接口三：获取会话消息列表

- 路径：`/ai/history/{type}/{chatId}`
- 方法：GET
- 路径参数：
  - `type` (string)
  - `chatId` (string)
- 响应：JSON Array of `MessageVO`，`MessageVO` 结构：

```json
{
  "role": "user|assistant|",
  "content": "文本"
}
```

- 响应示例：

```json
[
  {"role":"user","content":"你好"},
  {"role":"assistant","content":"你好，有什么可以帮忙的？"}
]
```

---

## 接口四：基于 PDF 的聊天（流式）

- 路径：`/ai/pdf/chat`
- 方法：GET 或 POST
- 参数：
  - `prompt` (string)
  - `chatId` (string) — 用于定位已上传的 PDF 文件
- 行为：若 `chatId` 对应文件不存在，后端会抛出异常（可能返回 500）。
- 响应：流式文本（text/html;charset=utf-8），同 `/ai/chat` 的消费方式。

---

## 接口五：上传 PDF

- 路径：`/ai/pdf/upload/{chatId}`
- 方法：POST（multipart/form-data）
- 路径参数：`chatId` (string)
- 表单字段：`file`（文件，Content-Type 应为 `application/pdf`）
- 请求 curl 示例：

```bash
curl -X POST "http://<host>/ai/pdf/upload/chat123" -F "file=@./doc.pdf;type=application/pdf"
```

- 响应：JSON `Result` 对象，结构：

```json
{
  "ok": 1, // 1 表示成功，0 表示失败
  "msg": "ok" // 或失败原因
}
```

- 成功示例： `{ "ok":1, "msg":"ok" }`
- 常见失败示例： `{ "ok":0, "msg":"只能上传PDF文件！" }`

---

## 接口六：下载 PDF 文件

- 路径：`/ai/pdf/file/{chatId}`
- 方法：GET
- 路径参数：`chatId` (string)
- 响应：文件流，响应头含 `Content-Disposition: attachment; filename="..."`，Content-Type: `application/octet-stream`。
- 前端下载示例（直接链接）：

```html
<a href="/ai/pdf/file/chat123" download>下载 PDF</a>
```

或使用 fetch 获取 blob：

```javascript
const r = await fetch('/ai/pdf/file/chat123');
if(r.ok){
  const blob = await r.blob();
  const url = URL.createObjectURL(blob);
  // 创建 a 标签下载
}
```

---

## 类型定义（前端参考）

- MessageVO (示例 TypeScript 接口)：

```ts
interface MessageVO {
  role: 'user' | 'assistant' | '';
  content: string;
}
```

- Result：

```ts
interface Result {
  ok: number; // 1 or 0
  msg: string;
}
```

---

如果你需要，我可以：

- 生成 Postman collection 或 Swagger/OpenAPI JSON
- 生成 TypeScript 的 API 封装函数
- 把文档放到 `docs/` 目录并提交

请告诉我下一步想要的格式。
