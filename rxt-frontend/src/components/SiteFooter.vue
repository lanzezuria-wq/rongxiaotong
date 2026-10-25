<script setup>
import BrandMark from './BrandMark.vue'
</script>
<template>
  <footer class="site-footer"><div class="footer-inner">
    <div><BrandMark compact /><p>连接田间好物、农业资金与专业知识。</p></div>
    <div class="footer-links"><router-link to="/products">农产品集市</router-link><router-link to="/financing/groups">联保小组</router-link><router-link to="/experts">咨询专家</router-link><router-link to="/user/profile">个人中心</router-link></div>
    <div class="footer-note"><span>课程项目 · 作品展示</span><small>金融服务为演示流程，未接入真实征信与放款。</small><small>梯田摄影：King of Hearts / <a href="https://commons.wikimedia.org/wiki/File:Longsheng_Rice_Terraces_November_2017_014.jpg" target="_blank" rel="noreferrer">Wikimedia Commons</a> · CC BY-SA 4.0</small><small><a href="/images/credits.html">查看全部素材来源与许可</a> · 商品图片仅为演示素材。</small></div>
  </div></footer>
</template>
