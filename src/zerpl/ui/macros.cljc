(ns zerpl.ui.macros)

(comment
 (defmacro cur-roman-gen []
   #?(:clj
      (-> (zerpl.core/current-generation)
          (gn/->roman)))))
