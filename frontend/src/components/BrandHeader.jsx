import ThemeSelector from './ThemeSelector'

function BrandHeader({ compact = false }) {
  return (
    <header className={`brand-header${compact ? ' brand-header--compact' : ''}`}>
      <div className="brand-header__identity">
        <p className="brand-header__name">UCTale</p>
        <p className="brand-header__tagline">장면과 선택으로 이어지는 당신의 이야기</p>
      </div>
      <ThemeSelector />
    </header>
  )
}

export default BrandHeader
