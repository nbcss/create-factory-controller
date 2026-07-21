require 'nokogiri'
require 'digest'
require 'ffi-icu'

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

  # Get the endonym of a langauge.
  def self.lang_name(lang)
    locale = ICU::Locale.new(lang)
    locale.with_locale_display_name(lang, [1]) do |names|
      ICU::Lib::Util.read_uchar_buffer(256) do |buffer, status|
        ICU::Lib.uldn_localeDisplayName(names, locale.id, buffer, buffer.size, status)
      end
    end
  end

  # Get all Git commit authors of a file that still has a line present
  def self.git_file_authors(path)
    return [] unless File.exist?(path)

    IO.popen(%W(git blame --incremental -- #{path}), &:readlines)
      .tap{raise unless $?.success?}
      .filter_map{|x| x.match('(?<=^author ).+$') }
      .map(&:to_s).uniq
  end

end
