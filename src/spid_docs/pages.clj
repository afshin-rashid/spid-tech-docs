(ns spid-docs.pages
  "Collects all kinds of pages from various sources"
  (:require [spid-docs.pages.api-pages :as apis]
            [spid-docs.pages.article-pages :as articles]
            [spid-docs.pages.endpoint-pages :as endpoints]
            [spid-docs.pages.frontpage :as frontpage]
            [spid-docs.pages.type-pages :as types]
            [stasis.core :as stasis]))

(defn get-pages [content]
  "Returns a map of pages for Stasis to serve"
  (stasis/merge-page-sources
   {:general-pages {"/" (partial frontpage/create-page (:apis content))}
    :endpoints (endpoints/create-pages (:endpoints content) (:types content) (:params content))
    :articles (articles/create-pages (:articles content))
    :types (types/create-pages (:types content))
    :apis (apis/create-pages (:apis content))}))
