package com.example.globalnewsenginev1.articles;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ArticleRepository extends JpaRepository<Article, Long> {

    Optional<Article> findByCanonicalUrl(String canonicalUrl);
}
