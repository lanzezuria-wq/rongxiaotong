<script setup>
import { ref, onMounted } from 'vue'
import { getBanners, getRecommended, getHomeArticles, getStats } from '@/api/home'
import ProductCard from '@/components/ProductCard.vue'

/**
 * 首页 —— 聚合轮播、统计、推荐商品、热门文章四块内容（SRS 首页聚合页）。
 *
 * <p>全部数据对游客开放，进页面直接拉。四块数据彼此独立，
 * 用 Promise.all 并发请求，避免串行等待把首屏拖慢。
 */
const loading = ref(false)
const banners = ref([])
const recommended = ref([])
const articles = ref([])
const stats = ref({ productCount: 0, financingSuccess: 0, userCount: 0 })

async function load() {
  loading.value = true
  try {
    const [b, r, a, s] = await Promise.all([
      getBanners(),
      getRecommended(),
      getHomeArticles(),
      getStats()
    ])
    banners.value = b || []
    recommended.value = r || []
    articles.value = a || []
    stats.value = s || {}
  } catch (e) {
    // 拦截器已统一提示
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="home" v-loading="loading">
    <!-- 轮播图 / 兜底 Hero：字典里没配 banner 时用静态横幅兜底 -->
    <section class="hero">
      <el-carousel v-if="banners.length" height="360px" class="hero-carousel">
        <el-carousel-item v-for="b in banners" :key="b.id">
          <el-image :src="b.imageUrl" fit="cover" class="hero-img">
            <template #error>
              <div class="hero-fallback">
                <h1>融销通 · 让农产品卖得更好</h1>
                <p>农产品交易 · 融资服务 · 专家助力 一站式农业金融服务平台</p>
              </div>
            </template>
          </el-image>
          <div class="hero-caption">{{ b.title }}</div>
        </el-carousel-item>
      </el-carousel>
      <div v-else class="hero-fallback">
        <h1>融销通 · 让农产品卖得更好</h1>
        <p>农产品交易 · 融资服务 · 专家助力 一站式农业金融服务平台</p>
      </div>
    </section>

    <!-- 平台统计（Mock 数字） -->
    <section class="container stats">
      <div class="stat-card">
        <div class="stat-num">{{ stats.productCount }}</div>
        <div class="stat-label">在售商品</div>
      </div>
      <div class="stat-card">
        <div class="stat-num">{{ stats.financingSuccess }}</div>
        <div class="stat-label">融资成功（笔）</div>
      </div>
      <div class="stat-card">
        <div class="stat-num">{{ stats.userCount }}</div>
        <div class="stat-label">注册用户</div>
      </div>
    </section>

    <!-- 热卖推荐 -->
    <section class="container section">
      <div class="section-head">
        <h2 class="section-title">热卖推荐</h2>
        <el-link type="primary" @click="$router.push('/products')">
          查看全部 <el-icon><ArrowRight /></el-icon>
        </el-link>
      </div>
      <div v-if="recommended.length" class="product-grid">
        <ProductCard v-for="p in recommended" :key="p.id" :product="p" />
      </div>
      <el-empty v-else description="暂无在售商品" />
    </section>

    <!-- 热门知识 -->
    <section class="container section">
      <div class="section-head">
        <h2 class="section-title">农业知识</h2>
      </div>
      <el-card v-if="articles.length" shadow="never">
        <div v-for="(a, i) in articles" :key="a.id" class="article-item">
          <span class="article-index">{{ i + 1 }}</span>
          <span class="article-title">{{ a.title }}</span>
          <span class="article-views"><el-icon><View /></el-icon> {{ a.viewCount ?? 0 }}</span>
        </div>
      </el-card>
      <el-empty v-else description="暂无文章" />
    </section>
  </div>
</template>

<style scoped>
.home {
  min-height: 60vh;
}

/* ---------- 轮播 ---------- */
.hero {
  margin-bottom: 24px;
}

.hero-carousel .hero-img {
  width: 100%;
  height: 360px;
  display: block;
}

.hero-caption {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 12px 24px;
  color: #fff;
  font-size: 15px;
  background: linear-gradient(transparent, rgba(0, 0, 0, 0.45));
}

.hero-fallback {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 360px;
  color: #fff;
  background: linear-gradient(120deg, #16a34a 0%, #0e7a3f 55%, #0b5c31 100%);
}

.hero-fallback h1 {
  margin: 0 0 12px;
  font-size: 30px;
  font-weight: 600;
}

.hero-fallback p {
  margin: 0;
  font-size: 14px;
  opacity: 0.9;
}

/* ---------- 统计 ---------- */
.stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.stat-card {
  padding: 24px;
  text-align: center;
  background: #fff;
  border-radius: 8px;
}

.stat-num {
  font-size: 28px;
  font-weight: 700;
  color: #16a34a;
}

.stat-label {
  margin-top: 6px;
  font-size: 13px;
  color: #8a9099;
}

/* ---------- 区块 ---------- */
.section {
  margin-bottom: 32px;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.section-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #1f2329;
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
}

/* ---------- 文章列表 ---------- */
.article-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 4px;
  border-bottom: 1px solid #f0f2f5;
}

.article-item:last-child {
  border-bottom: none;
}

.article-index {
  width: 20px;
  height: 20px;
  line-height: 20px;
  text-align: center;
  font-size: 12px;
  font-weight: 600;
  color: #fff;
  background: #16a34a;
  border-radius: 4px;
  flex-shrink: 0;
}

.article-title {
  flex: 1;
  font-size: 14px;
  color: #1f2329;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.article-views {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #8a9099;
  flex-shrink: 0;
}
</style>
