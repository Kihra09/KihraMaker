# Loaded by KihraMaker before the game's own scripts. Generated settings: __RGSS__ = RGSS version (1, 2 or 3).
KIHRA_RGSS = __RGSS__

# ---- Old Ruby (1.9) methods that RPG Maker VX Ace games still use ----
begin
  class Hash
    unless method_defined?(:index)
      def index(value)
        key(value)
      end
    end
  end
  Fixnum = Integer unless defined?(Fixnum)
  Bignum = Integer unless defined?(Bignum)
rescue StandardError
end

# ---- Windows-only DLLs (steam_api, plugins) can't load here: calls become harmless no-ops ----
begin
  module KihraSafeFFI
    SEEN = {}
    def self.log(lib, func, args)
      text = args.map { |a| a.is_a?(String) ? a.inspect : a.to_s }.join(", ")
      return unless func =~ /achiev|unlock|trophy/i || text =~ /achiev|unlock|trophy/i
      line = lib + "\t" + func + "\t" + text
      return if SEEN[line]
      SEEN[line] = true
      begin
        File.open("kihra_ach.log", "a") { |f| f.puts line }
      rescue StandardError
      end
    end
    def initialize(lib, func, *rest, &blk)
      super
    rescue RuntimeError, LoadError, NameError
      @kihra_missing = true
      @kihra_lib = lib.to_s
      @kihra_func = func.to_s
    end
    def call(*args)
      if @kihra_missing
        KihraSafeFFI.log(@kihra_lib, @kihra_func, args)
        return 0
      end
      super
    end
    def Call(*args)
      call(*args)
    end
  end
  MiniFFI.send(:prepend, KihraSafeFFI)
rescue StandardError
end

# ---- Remote control from the app: fast forward and cheats ----
# The app writes commands into kihra_cmd.txt (one per line); they run between frames.
module KihraCtl
  @speed = 1.0
  @base = nil
  @god = false
  @tick = 0

  class << self
    attr_reader :speed

    def poll
      @tick += 1
      return unless @tick % 8 == 0
      guard { heal } if @god
      return unless File.exist?("kihra_cmd.txt")
      lines = nil
      begin
        File.rename("kihra_cmd.txt", "kihra_cmd.work")
        lines = File.read("kihra_cmd.work").split("\n")
        File.delete("kihra_cmd.work")
      rescue StandardError
        return
      end
      lines.each { |l| guard { run(l.strip) } }
    end

    def guard
      yield
    rescue StandardError, NoMethodError
      nil
    end

    def run(line)
      cmd, a, b = line.split(" ")
      case cmd
      when "speed" then set_speed(a.to_f)
      when "gold" then $game_party.gain_gold(a.to_i) if $game_party
      when "heal" then heal
      when "god" then @god = !@god
      when "through" then $game_player.through = !$game_player.through if $game_player
      when "noenc" then $game_system.encounter_disabled = !$game_system.encounter_disabled if $game_system.respond_to?(:encounter_disabled)
      when "item" then add_item(a.to_i, (b || 1).to_i)
      when "switch"
        $game_switches[a.to_i] = (b.to_i != 0)
        $game_map.need_refresh = true if $game_map
      when "var"
        $game_variables[a.to_i] = b.to_i
        $game_map.need_refresh = true if $game_map
      when "level" then level_up
      when "qsave" then ack("qsave", quick_save)
      when "qload" then ack("qload", quick_load)
      when "dump" then dump_db
      end
    end

    QSLOT = 98 # file Save99: never shown in the game's own save screen (16 slots)

    def ack(name, result)
      File.open("kihra_ack.txt", "w") { |f| f.write("#{name}\t#{result}") }
    rescue StandardError
    end

    # Quick save / load use the game's own save system (VX Ace only). This is NOT a true
    # save state: it saves like the game's menu does, so it works where the game lets you save.
    def quick_save
      return "unsupported" unless KIHRA_RGSS == 3 && defined?(DataManager)
      return "notmap" unless SceneManager.scene.is_a?(Scene_Map)
      DataManager.save_game(QSLOT) ? "ok" : "failed"
    rescue Exception
      "failed"
    end

    def quick_load
      return "unsupported" unless KIHRA_RGSS == 3 && defined?(DataManager)
      return "nofile" unless File.exist?(DataManager.make_filename(QSLOT))
      return "battle" if SceneManager.scene.is_a?(Scene_Battle)
      return "failed" unless DataManager.load_game(QSLOT)
      $game_system.on_after_load
      SceneManager.goto(Scene_Map)
      "ok"
    rescue Exception
      "failed"
    end

    # Writes the game's switch / variable / item names with their current values, so the app can
    # show cheats made for this particular game.
    def dump_db
      out = []
      clean = lambda { |t| t.to_s.gsub(/[\t\r\n]/, " ").strip }
      if $data_system
        sw = $data_system.switches
        va = $data_system.variables
        (1...sw.size).each do |i|
          n = clean.call(sw[i])
          next if n.empty?
          out << "S\t#{i}\t#{n}\t#{$game_switches[i] ? 1 : 0}"
        end
        (1...va.size).each do |i|
          n = clean.call(va[i])
          next if n.empty?
          out << "V\t#{i}\t#{n}\t#{$game_variables[i].to_i}"
        end
      end
      if $data_items && $game_party
        (1...$data_items.size).each do |i|
          it = $data_items[i]
          next unless it
          n = clean.call(it.name)
          next if n.empty?
          cnt = KIHRA_RGSS == 1 ? $game_party.item_number(i) : $game_party.item_number(it)
          out << "I\t#{i}\t#{n}\t#{cnt.to_i}"
        end
      end
      File.open("kihra_db.txt", "w:UTF-8") { |f| f.write(out.first(4000).join("\n")) }
    rescue Exception
    end

    def set_speed(x)
      @base ||= Graphics.frame_rate
      x = 1.0 if x < 1.0
      Graphics.frame_rate = [(@base * x).round, 960].min
      @speed = x
    end

    def heal
      return unless $game_party
      if KIHRA_RGSS == 1
        $game_party.actors.each { |a| a.hp = a.maxhp; a.sp = a.maxsp }
      else
        $game_party.members.each { |a| a.recover_all }
      end
    end

    def add_item(id, n)
      return unless $game_party && id > 0
      if KIHRA_RGSS == 1
        $game_party.gain_item(id, n)
      elsif $data_items && $data_items[id]
        $game_party.gain_item($data_items[id], n)
      end
    end

    def level_up
      return unless KIHRA_RGSS == 3 && $game_party
      $game_party.members.each { |a| a.change_level(a.level + 1, false) }
    end
  end
end

begin
  module KihraGraphicsHook
    def update(*args)
      r = super
      KihraCtl.poll
      r
    end
  end
  Graphics.singleton_class.prepend(KihraGraphicsHook)
rescue StandardError
end
