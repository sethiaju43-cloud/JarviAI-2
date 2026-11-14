import os

base = "app/src/main/java/com/jarvis/app"
manifest = "app/src/main/AndroidManifest.xml"

# 1) In pura files ko delete karo
to_delete = ["LocalLlm.kt", "LocalModelCatalog.kt", "ModelDownloadService.kt", "ModelFit.kt", "ModelsActivity.kt"]
for f in to_delete:
    p = os.path.join(base, f)
    if os.path.exists(p):
        os.remove(p)
        print("deleted:", p)
    else:
        print("MILA NAHIN (already gone?):", p)

def edit(path, old, new, label):
    with open(path, "r", encoding="utf-8") as f:
        data = f.read()
    if old not in data:
        print("MILA NAHIN ->", label, "in", path)
        return
    data = data.replace(old, new, 1)
    with open(path, "w", encoding="utf-8") as f:
        f.write(data)
    print("theek ho gaya ->", label)

# 2) AndroidManifest.xml se activity + service hatao
edit(manifest,
     '        <activity android:name=".ModelsActivity" android:exported="false" />\n',
     '',
     "manifest activity")

edit(manifest,
     '''        <service
            android:name=".ModelDownloadService"
            android:exported="false"
            android:foregroundServiceType="dataSync" />

''',
     '',
     "manifest service")

# 3) JarvisApp.kt se onTrimMemory hatao
edit(os.path.join(base, "JarvisApp.kt"),
     '''
    /** Phone ki memory kam ho to local model hata do (crash se bachne ke liye) */
    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        LocalLlm.onTrim(level)
    }
''',
     '',
     "JarvisApp onTrimMemory")

# 4) ModelRouter.kt: LocalBackend class hatao (comment se agle object tak), aur functions ko null/false bana do
p = os.path.join(base, "ModelRouter.kt")
with open(p, "r", encoding="utf-8") as f:
    data = f.read()
start_marker = "/** Local model ko router ke liye backend banata hai */"
end_marker = "/** Kaun sa model chalega ye tay karta hai: online/local, fallback, retry, status */"
si = data.find(start_marker)
ei = data.find(end_marker)
if si == -1 or ei == -1 or ei <= si:
    print("MILA NAHIN -> ModelRouter LocalBackend block")
else:
    data = data[:si] + data[ei:]
    print("theek ho gaya -> ModelRouter LocalBackend class removed")
with open(p, "w", encoding="utf-8") as f:
    f.write(data)

edit(p,
     '''    fun localBackend(ctx: Context): Backend? =
        LocalLlm.activeModel(ctx)?.let { LocalBackend(ctx, it) }

    fun avoidLocal(ctx: Context): Boolean =
        Prefs.batterySaver(ctx) && ModelFit.shouldAvoidLocal(DeviceInfo.snapshot(ctx))
''',
     '''    fun localBackend(ctx: Context): Backend? = null

    fun avoidLocal(ctx: Context): Boolean = false
''',
     "ModelRouter localBackend/avoidLocal")

# 5) DashboardActivity.kt se "Local models" section hatao
edit(os.path.join(base, "DashboardActivity.kt"),
     '''        // ---------- Local models ----------
        container.addView(UiKit.section(c, "📱 Local models (offline)"))
        val installed = try { LocalModelCatalog.all(c).filter { ModelDownloader.isInstalled(c, it) } } catch (e: Exception) { emptyList() }
        val lm = UiKit.card(c)
        if (installed.isEmpty()) {
            lm.addView(UiKit.text(c, "Abhi koi local model installed nahi. Settings → Local AI models se download karo; tab online calls kam hongi.", 13f, dim = true))
        } else {
            val loaded = try { LocalLlm.loadedModelId() } catch (e: Exception) { null }
            for (m in installed) {
                lm.addView(UiKit.text(c, "✅ " + m.name + " (" + m.sizeMb + " MB)" + (if (m.id == loaded) " · abhi memory mein" else ""), 13f))
            }
        }
        container.addView(lm)

''',
     '',
     "DashboardActivity local-models section")

# 6) SettingsActivity.kt se model-download button aur Battery section hatao
edit(os.path.join(base, "SettingsActivity.kt"),
     '''        container.addView(UiKit.button(c, "📱 Local AI models (offline) + download manager") {
            startActivity(Intent(c, ModelsActivity::class.java))
        })
''',
     '',
     "SettingsActivity local-models button")

edit(os.path.join(base, "SettingsActivity.kt"),
     '''        // ---------- Battery ----------
        container.addView(UiKit.section(c, "🔋 Battery aur performance"))
        container.addView(UiKit.switchRow(c, "Phone garam / battery kam ho to local model se bacho", Prefs.batterySaver(c)) { Prefs.setBool(c, "battery_saver", it) })
        val idle = Prefs.idleUnloadMin(c)
        container.addView(UiKit.button(c, "⏱ Local model kitni der baad hatayein: " + (if (idle <= 0) "kabhi nahi" else "$idle min")) {
            val opts = arrayOf("1 min", "3 min", "10 min", "Kabhi nahi")
            val vals = intArrayOf(1, 3, 10, 0)
            AlertDialog.Builder(c).setTitle("Idle unload").setItems(opts) { _, i ->
                Prefs.setInt(c, "local_idle_min", vals[i]); rebuild()
            }.show()
        })
        container.addView(UiKit.switchRow(c, "Local model par GPU (experimental, kuch phones par crash kar sakta hai)", Prefs.localGpu(c)) {
            Prefs.setBool(c, "local_gpu", it); LocalLlm.unload("setting badli")
        })

''',
     '',
     "SettingsActivity battery section")

# 7) ChatActivity.kt se model button/menu hatao
edit(os.path.join(base, "ChatActivity.kt"),
     '        b.tvModel.setOnClickListener { startActivity(Intent(this, ModelsActivity::class.java)) }\n',
     '',
     "ChatActivity tvModel click")

edit(os.path.join(base, "ChatActivity.kt"),
     '        pm.menu.add(0, 3, 2, "📱 Local AI models")\n',
     '',
     "ChatActivity menu item")

edit(os.path.join(base, "ChatActivity.kt"),
     '                3 -> startActivity(Intent(this, ModelsActivity::class.java))\n',
     '',
     "ChatActivity menu branch")

print("SAB KAAM HO GAYA")
