# Production: API keys ko server par rakho (optional)

App mein "Custom / apna backend (OpenAI-compatible)" provider hai. Isse aap apne server ka
`https://.../v1` URL de sakte ho, aur asli provider ki secret key sirf server par rehti hai
(app ya APK mein kabhi nahi).

Chhota Node.js (Express) example, jo `/v1/chat/completions` ko Groq par bhejta hai:

```js
import express from "express";
const app = express();
app.use(express.json({ limit: "2mb" }));

app.post("/v1/chat/completions", async (req, res) => {
  if (req.headers.authorization !== `Bearer ${process.env.APP_TOKEN}`) return res.sendStatus(401);
  const r = await fetch("https://api.groq.com/openai/v1/chat/completions", {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${process.env.GROQ_KEY}` },
    body: JSON.stringify(req.body),
  });
  res.status(r.status).send(await r.text());
});

app.get("/v1/models", (_req, res) => res.json({ data: [{ id: "llama-3.3-70b-versatile" }] }));
app.listen(3000);
```

App mein: Providers → Custom → Base URL `https://aapka-server/v1`, API key = `APP_TOKEN`
(sirf aapke server ka token, asli provider key nahi), model naam daalo. Server par rate limit zaroor lagao.
Ye example test nahi kiya gaya, sirf rasta dikhane ke liye hai.
