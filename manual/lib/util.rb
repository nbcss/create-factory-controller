require 'nokogiri'
require 'digest'

module Util

  # Flatten hash, concatenate keys with dots
  def self.flatten_hash(hash)
    hash.each_with_object({}) do |(key, value), flattened|
      if value.is_a?(Hash)
        flatten_hash(value).each { |child_key, child_value| flattened["#{key}.#{child_key}"] = child_value }
      else
        flattened[key.to_s] = value
      end
    end
  end

  # Appends a SHA-1 cache-busting query string to local file +path+.
  def self.add_cache_id(path, base_path)
    return path if path.include?('://')

    absolute = File.expand_path(path, File.dirname(base_path))
    return path unless File.file?(absolute)

    digest = Digest::SHA1.file(absolute).hexdigest
    "#{path}?v=#{digest}"
  end

  # Rewrites local <img> paths to append a cache-busting query.
  def self.cache_id_images(html, base_path)
    doc = Nokogiri::HTML5.fragment html

    doc.css('img[src]').each do |img|
      img['src'] = add_cache_id(img['src'], base_path)
    end

    doc.to_html
  end

  # Strip the outermost elements, optionally strip 'class' attributes
  def self.html_strip(html, layers: 1, strip_class: false)
    doc = Nokogiri::HTML5.fragment html

    layers.times do
      doc = doc.children[0]
      return nil if !doc
    end

    if strip_class
      doc.css('*').each{|e| e.remove_attribute 'class' }
    end
    doc.children
  end

end
