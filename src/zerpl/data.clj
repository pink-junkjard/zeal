(ns zerpl.data)

(def data
  [
   ;; CSS
   {:css-spec/attribute       :border-style
    :css-spec/possible-values ["none" "hidden" "dotted" "dashed" "solid" "double" "groove" "ridge" "inset" "outset"]}
   {:css-spec/attribute       :font-family
    :css-spec/possible-values ["Segoe UI", "Tahoma", "Book Antiqua", "Papyrus", "Helvetica", "Monaco", "Trebuchet MS", "Copperplate", "Calisto MT", "Arial Black", "Didot", "Hoefler Text", "Cambria", "Avant Garde", "Goudy Old Style", "Century Gothic", "Gill Sans", "Arial", "Baskerville", "Geneva", "Lucida Grande", "Verdana", "Brush Script MT", "Lucida Console", "Candara", "Garamond", "Lucida Sans Typewriter", "Franklin Gothic Medium", "Georgia", "Palatino", "Arial Rounded MT Bold", "Lucida Bright", "Futura", "Courier New", "Big Caslon", "Rockwell Extra Bold", "Bodoni MT", "Optima", "Calibri", "Consolas", "Impact", "Arial Narrow", "Andale Mono", "Times New Roman", "Rockwell", "Perpetua"]}
   {:css-spec/attribute   :font-size
    :css-spec/value-type  :int
    :css-spec/value-unit  :px
    :css-spec/value-range [10 22]}
   {:css-spec/attribute   :padding
    :css-spec/value-type  :int
    :css-spec/value-unit  :px
    :css-spec/value-range [0 20]}
   {:css-spec/attribute   :margin
    :css-spec/value-type  :int
    :css-spec/value-unit  :px
    :css-spec/value-range [0 20]}
   {:css-spec/attribute   :border-width
    :css-spec/value-type  :int
    :css-spec/value-unit  :px
    :css-spec/value-range [1 10]}
   {:css-spec/attribute   :border-radius
    :css-spec/value-type  :int
    :css-spec/value-unit  :px
    :css-spec/value-range [1 10]}
   {:css-spec/attribute       :font-weight
    :css-spec/possible-values [100, 300, 600, 800]}
   {:css-spec/attribute    :background
    :css-spec/value-type   :hsl
    :css-spec/value-ranges [[0 360] [80 100] [:constant 70]]}
   {:css-spec/attribute    :color
    :css-spec/value-type   :hsl
    :css-spec/value-ranges [[0 360] [80 100] [:constant 30]]}

   ;; VIEWS
   {:view/name           :nav-store-name
    :view/css-attributes #{:font-family}}
   {:view/name           :product-title
    :view/css-attributes #{:color :font-size}}
   {:view/name           :product-image
    :view/css-attributes #{:border-style :border-width :border-radius}}
   {:view/name           :product-description
    :view/css-attributes #{:font-family :font-size}}
   {:view/name           :product-card
    :view/content        #{[:view/name :product-title] [:view/name :product-description]}
    :view/css-attributes #{:margin :padding :border-style :border-width :border-radius}}
   {:view/name           :products
    :view/content        #{[:view/name :product-title] [:view/name :product-description]}
    :view/css-attributes #{}}
   {:view/name           :app
    :view/content        #{[:view/name :products]}
    :view/css-attributes #{}}

   {:crux.db/id              :shop/current-generation
    :shop/current-generation 1}


   {:db/id        -150
    :product/name "Mouth Wash"}

   {:db/id        -151
    :product/name "zipError"}

   {:db/id               -200
    :view-instance/view  [:view/name :product-title]
    :view-instance/props -150}
   {:db/id               -201
    :view-instance/view  [:view/name :product-title]
    :view-instance/props -151}
   {:db/id                 -202
    :view-instance/view    [:view/name :products]
    :view-instance/content #{-200 -201}}
   {:view-instance/view    [:view/name :app]
    :view-instance/version 1
    :view-instance/content #{-202}}

   ])
