(ns zeal.ui.macros)

(comment
 (defmacro cur-roman-gen []
   #?(:clj
      (-> (zeal.core/current-generation)
          (gn/->roman)))))
