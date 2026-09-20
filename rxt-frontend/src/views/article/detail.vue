<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getArticle, likeArticle, commentArticle } from '@/api/article'
import { isLoggedIn } from '@/utils/auth'

/**
 * 文章详情 —— 正文 + 点赞 + 评论（游客可读，点赞/评论需登录）。
 */
const route = useRoute()
const router = useRouter()

const loading = ref(false)
const article = ref(null)
const liking = ref(false)
const commentText = ref('')
const submitting = ref(false)

async function load() {
  loading.value = true
  try {
    article.value = await getArticle(route.params.id)
    document.title = `${article.value?.title || '文章详情'} · 融销通`
  } catch (e) {
    // 拦截器已统一提示
  } finally {
    loading.value = false
  }
}

function requireLogin() {
  if (!isLoggedIn()) {
    ElMessage.warning('请先登录')
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return false
  }
  return true
}

async function onLike() {
  if (!requireLogin()) return
  liking.value = true
  try {
    const res = await likeArticle(article.value.id)
    article.value.isLiked = res.isLiked
    article.value.likeCount = res.likeCount
  } catch (e) {
    // 拦截器已统一提示
  } finally {
    liking.value = false
  }
}

async function onSubmitComment() {
  if (!requireLogin()) return
  const content = commentText.value.trim()
  if (!content) {
    ElMessage.warning('请输入评论内容')
    return
  }
  submitting.value = true
  try {
    await commentArticle(article.value.id, content)
    ElMessage.success('评论发表成功')
    commentText.value = ''
    load()
  } catch (e) {
    // 拦截器已统一提示
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="article-detail-page">
    <div class="container">
      <div v-loading="loading" class="detail-wrap">
        <template v-if="article">
          <el-card shadow="never" class="article-body">
            <h1 class="ad-title">{{ article.title }}</h1>

            <div class="ad-meta">
              <span class="ad-author">{{ article.authorName || '匿名' }}</span>
              <el-tag v-if="article.authorRole === 'expert'" size="small" type="success" effect="plain">专家</el-tag>
              <span class="ad-dot">·</span>
              <span>{{ article.createdAt }}</span>
              <span class="ad-dot">·</span>
              <span>{{ article.viewCount }} 阅读</span>
            </div>

            <div v-if="article.tags?.length" class="ad-tags">
              <el-tag v-for="t in article.tags" :key="t" size="small" type="info" effect="plain">{{ t }}</el-tag>
            </div>

            <el-divider />

            <div class="ad-content">{{ article.content }}</div>

            <div class="ad-actions">
              <el-button :type="article.isLiked === 1 ? 'primary' : 'default'" :loading="liking" @click="onLike">
                <el-icon><Star /></el-icon>
                <span style="margin-left: 4px">{{ article.isLiked === 1 ? '已点赞' : '点赞' }} {{ article.likeCount ?? 0 }}</span>
              </el-button>
              <span class="ad-comment-count">评论 {{ article.commentCount ?? 0 }}</span>
            </div>
          </el-card>

          <el-card shadow="never" class="comment-card">
            <template #header><span class="card-title">评论（{{ article.comments?.length || 0 }}）</span></template>

            <div class="comment-input">
              <el-input v-model="commentText" type="textarea" :rows="3" maxlength="200" show-word-limit
                placeholder="写下你的评论（登录后发表）" />
              <el-button type="primary" class="comment-submit" :loading="submitting" @click="onSubmitComment">
                发表评论
              </el-button>
            </div>

            <div v-if="article.comments?.length" class="comment-list">
              <div v-for="c in article.comments" :key="c.id" class="comment-item">
                <div class="ci-head">
                  <span class="ci-user">{{ c.username || '匿名' }}</span>
                  <span class="ci-time">{{ c.createdAt }}</span>
                </div>
                <div class="ci-content">{{ c.content }}</div>
              </div>
            </div>
            <el-empty v-else description="暂无评论" :image-size="60" />
          </el-card>
        </template>

        <el-empty v-if="!loading && !article" description="文章不存在或未发布" style="padding: 60px 0">
          <el-button type="primary" @click="$router.push('/articles')">返回文章列表</el-button>
        </el-empty>
      </div>
    </div>
  </div>
</template>

<style scoped>
.article-detail-page {
  padding-bottom: 40px;
}

.detail-wrap {
  max-width: 860px;
}

.article-body {
  margin-bottom: 16px;
}

.ad-title {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: #1f2329;
  line-height: 1.4;
}

.ad-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 12px;
  font-size: 13px;
  color: #8a9099;
}

.ad-author {
  color: #16a34a;
  font-weight: 500;
}

.ad-dot {
  color: #d1d5db;
}

.ad-tags {
  margin-top: 12px;
  display: flex;
  gap: 6px;
}

.ad-content {
  font-size: 15px;
  line-height: 1.9;
  color: #374151;
  white-space: pre-wrap;
}

.ad-actions {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-top: 24px;
}

.ad-comment-count {
  font-size: 13px;
  color: #8a9099;
}

.comment-card .card-title {
  font-size: 15px;
  font-weight: 600;
  color: #1f2329;
}

.comment-input {
  margin-bottom: 16px;
}

.comment-submit {
  margin-top: 10px;
}

.comment-item {
  padding: 12px 0;
  border-bottom: 1px solid #f0f2f5;
}

.comment-item:last-child {
  border-bottom: none;
}

.ci-head {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
}

.ci-user {
  color: #1f2329;
  font-weight: 500;
}

.ci-time {
  color: #8a9099;
}

.ci-content {
  margin-top: 6px;
  font-size: 14px;
  color: #4b5563;
  line-height: 1.6;
}
</style>
