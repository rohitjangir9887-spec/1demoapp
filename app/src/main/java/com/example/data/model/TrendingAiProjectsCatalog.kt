package com.example.data.model

data class TrendingAiProject(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: String,
    val tags: List<String>,
    val prompt: String,
    val defaultScheduleMode: ScheduleMode = ScheduleMode.HOURLY,
    val taskCategory: TaskCategory = TaskCategory.RESEARCH,
    val priority: TaskPriority = TaskPriority.HIGH,
    val recommendedModels: List<String> = listOf("nvidia/llama-3.1-nemotron-70b-instruct", "meta/llama-3.3-70b-instruct")
)

/**
 * Curated catalog of trending public Google & GitHub AI project goals for autonomous execution.
 */
object TrendingAiProjectsCatalog {
    val projects: List<TrendingAiProject> = listOf(
        TrendingAiProject(
            id = "google_gemini_2_agent",
            title = "🤖 Google Gemini 2.0 Agent",
            subtitle = "Autonomous multi-modal reasoning & live web search agent",
            category = "Google AI",
            tags = listOf("Gemini 2.0", "Free API", "Agentic"),
            prompt = "Julie, execute Google Gemini 2.0 Agent workflow: perform live web search for today's breaking AI developments, analyze open-source agent frameworks, and generate an actionable implementation summary.",
            taskCategory = TaskCategory.RESEARCH
        ),
        TrendingAiProject(
            id = "github_repo_sentinel",
            title = "🐙 GitHub Public Repo Sentinel",
            subtitle = "Auto-inspect open source repositories, check issues & PRs",
            category = "GitHub Agent",
            tags = listOf("GitHub", "CI/CD", "Code Quality"),
            prompt = "Analyze trending GitHub AI repositories today. Extract top stars, architectural patterns, outstanding issues, and summarize how we can integrate their best components.",
            taskCategory = TaskCategory.CODE
        ),
        TrendingAiProject(
            id = "android_full_phone_agent",
            title = "📱 Android Full Phone Controller",
            subtitle = "Control YouTube, Chrome, Settings & installed apps via voice",
            category = "Automation",
            tags = listOf("Accessibility", "Voice", "Hands-Free"),
            prompt = "Check installed apps on the device, prepare multi-step voice workflows for YouTube, Chrome, and phone dialer, and verify all accessibility gesture coordinates.",
            taskCategory = TaskCategory.AUTOMATION
        ),
        TrendingAiProject(
            id = "global_ai_news_sentinel",
            title = "🌐 24/7 Global AI News Sentinel",
            subtitle = "Hourly autonomous crawling of global AI tech breakthroughs",
            category = "Research",
            tags = listOf("24/7 Swarm", "Live Web", "Hinglish"),
            prompt = "Fetch today's top 5 AI industry breakthroughs from global news sources, Wikipedia, and AI labs. Format into a structured intelligence briefing with verified citations.",
            taskCategory = TaskCategory.RESEARCH
        ),
        TrendingAiProject(
            id = "google_free_tools_catalog",
            title = "⚡ Google Free AI Tools 2026 Directory",
            subtitle = "Curated directory of zero-cost Google AI developer platforms",
            category = "Google AI",
            tags = listOf("Free Tier", "Google Cloud", "APIs"),
            prompt = "Compile a complete 2026 directory of Google's free AI tools (Gemini Free Tier, AI Studio, Gemma, Colab Free, Kaggle GPUs). Explain capabilities, quotas, and quick-start links.",
            taskCategory = TaskCategory.RESEARCH
        )
    )
}
